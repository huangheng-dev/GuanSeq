import "server-only";

import { randomUUID } from "node:crypto";

import {
  bomPageSchema,
  bomRecordSchema,
  bomReferenceDataSchema,
  type BomRecord,
  type BomReferenceData,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type BomWritePayload = {
  parentMaterialId: string;
  usageType: "PRODUCTION";
  versionCode: string;
  baseQuantity: number;
  effectiveFrom: string;
  owner: string;
  changeReason: string;
  lines: Array<{ componentMaterialId: string; quantity: number; scrapRate: number; note?: string | null }>;
};

export type BomMutation =
  | { operation: "create"; payload: BomWritePayload }
  | { operation: "update"; id: string; payload: BomWritePayload & { expectedVersion: number } }
  | { operation: "action"; id: string; action: "PUBLISH" | "INACTIVATE"; expectedVersion: number };

export type BomPageData = {
  source: "backend" | "unavailable";
  boms: BomRecord[];
  referenceData: BomReferenceData;
  error?: string;
};

const emptyReferences: BomReferenceData = { parentMaterials: [], componentMaterials: [] };

export async function getBomPageData(pathname: string): Promise<BomPageData | null> {
  if (pathname !== "/product/boms/list") return null;
  const requestId = `web-boms-${randomUUID()}`;
  const [listResponse, referenceResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/product/boms?page=0&size=200&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/product/bom-reference-data", requestId),
  ]);
  if (!listResponse?.ok || !referenceResponse?.ok) {
    return { source: "unavailable", boms: [], referenceData: emptyReferences, error: "BOM 服务暂时不可用，请稍后刷新重试。" };
  }
  return {
    source: "backend",
    boms: bomPageSchema.parse(await listResponse.json()).items,
    referenceData: bomReferenceDataSchema.parse(await referenceResponse.json()),
  };
}

export async function mutateBom(input: BomMutation, requestId: string) {
  let path = "/api/v1/product/boms";
  let method = "POST";
  let body: unknown;
  if (input.operation === "create") {
    body = input.payload;
  } else if (input.operation === "update") {
    path += `/${input.id}`;
    method = "PUT";
    body = input.payload;
  } else {
    path += `/${input.id}/actions`;
    body = { action: input.action, expectedVersion: input.expectedVersion };
  }
  const response = await requestGuanSeqApi(path, requestId, { method, body: JSON.stringify(body) }, 10000);
  if (!response) throw new GuanSeqApiError("BOM 服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "BOM 操作失败");
  return {
    bom: bomRecordSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}
