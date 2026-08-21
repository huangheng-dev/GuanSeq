import "server-only";

import { randomUUID } from "node:crypto";

import { routingPageSchema, routingRecordSchema, routingReferenceDataSchema, type RoutingRecord, type RoutingReferenceData } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type RoutingWritePayload = {
  materialId: string;
  usageType: "PRODUCTION";
  versionCode: string;
  baseQuantity: number;
  effectiveFrom: string;
  owner: string;
  changeReason: string;
  operations: Array<{
    operationCode: string;
    operationName: string;
    workCenterCode: string;
    workCenterName: string;
    setupMinutes: number;
    runMinutesPerUnit: number;
    queueMinutes: number;
    inspectionRequired: boolean;
    instructionSummary?: string | null;
  }>;
};

export type RoutingMutation =
  | { operation: "create"; payload: RoutingWritePayload }
  | { operation: "update"; id: string; payload: RoutingWritePayload & { expectedVersion: number } }
  | { operation: "action"; id: string; action: "PUBLISH" | "INACTIVATE"; expectedVersion: number };

export type RoutingPageData = {
  source: "backend" | "unavailable";
  routings: RoutingRecord[];
  referenceData: RoutingReferenceData;
  error?: string;
};

const emptyReferences: RoutingReferenceData = { materials: [] };

export async function getRoutingPageData(pathname: string): Promise<RoutingPageData | null> {
  if (pathname !== "/product/routings/list") return null;
  const requestId = `web-routings-${randomUUID()}`;
  const [listResponse, referenceResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/product/routings?page=0&size=200&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/product/routing-reference-data", requestId),
  ]);
  if (!listResponse?.ok || !referenceResponse?.ok) {
    return { source: "unavailable", routings: [], referenceData: emptyReferences, error: "工艺路线服务暂时不可用，请稍后刷新重试。" };
  }
  return {
    source: "backend",
    routings: routingPageSchema.parse(await listResponse.json()).items,
    referenceData: routingReferenceDataSchema.parse(await referenceResponse.json()),
  };
}

export async function mutateRouting(input: RoutingMutation, requestId: string) {
  let path = "/api/v1/product/routings";
  let method = "POST";
  let body: unknown;
  if (input.operation === "create") body = input.payload;
  else if (input.operation === "update") { path += `/${input.id}`; method = "PUT"; body = input.payload; }
  else { path += `/${input.id}/actions`; body = { action: input.action, expectedVersion: input.expectedVersion }; }
  const response = await requestGuanSeqApi(path, requestId, { method, body: JSON.stringify(body) }, 10000);
  if (!response) throw new GuanSeqApiError("工艺路线服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "工艺路线操作失败");
  return { routing: routingRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
