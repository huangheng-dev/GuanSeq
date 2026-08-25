import "server-only";

import { randomUUID } from "node:crypto";

import { equipmentSparePartPageSchema, equipmentSparePartReferenceSchema, equipmentSparePartSchema,
  type EquipmentSparePartPage, type EquipmentSparePartReference } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type EquipmentSparePartMutation = { materialId: string; preferredWarehouseId: string; reorderPoint: number; reason: string };
export type EquipmentSparePartPageData =
  | { source: "backend"; page: EquipmentSparePartPage; references: EquipmentSparePartReference; requestId: string }
  | { source: "unavailable"; page: null; references: null; status: number; message: string; requestId: string };

async function responseMessage(response: Response, fallback: string) {
  try { return ((await response.json()) as { message?: string }).message ?? fallback; } catch { return fallback; }
}

export async function loadEquipmentSparePartPage(requestId: string): Promise<EquipmentSparePartPageData> {
  const [pageResponse, referenceResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/equipment/spare-parts?page=0&size=200", requestId),
    requestGuanSeqApi("/api/v1/equipment/spare-parts/references", `${requestId}-references`),
  ]);
  if (!pageResponse || !referenceResponse) return { source: "unavailable", page: null, references: null, status: 503,
    message: "设备备件服务暂时不可用", requestId };
  const responseRequestId = pageResponse.headers.get("X-Request-Id") ?? requestId;
  if (!pageResponse.ok || !referenceResponse.ok) {
    const failed = !pageResponse.ok ? pageResponse : referenceResponse;
    return { source: "unavailable", page: null, references: null, status: failed.status,
      message: await responseMessage(failed, "设备备件台账加载失败"), requestId: responseRequestId };
  }
  return { source: "backend", page: equipmentSparePartPageSchema.parse(await pageResponse.json()),
    references: equipmentSparePartReferenceSchema.parse(await referenceResponse.json()), requestId: responseRequestId };
}

export async function getEquipmentSparePartPageData(pathname: string) {
  return pathname === "/equipment/spare-parts" ? loadEquipmentSparePartPage(`web-equipment-spare-parts-${randomUUID()}`) : null;
}

export async function createEquipmentSparePart(input: EquipmentSparePartMutation, requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/equipment/spare-parts", requestId,
    { method: "POST", body: JSON.stringify(input) }, 10000);
  if (!response) throw new GuanSeqApiError("设备备件服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "备件台账建立失败");
  return { sparePart: equipmentSparePartSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
