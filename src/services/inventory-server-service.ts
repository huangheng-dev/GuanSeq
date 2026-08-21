import "server-only";

import { randomUUID } from "node:crypto";
import { inventoryPageSchema, inventoryRecordSchema, inventoryReferenceDataSchema, type InventoryMovementType, type InventoryRecord, type InventoryReferenceData } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type InventoryPageData = { source: "backend" | "unavailable"; balances: InventoryRecord[]; referenceData: InventoryReferenceData; error?: string };
export type InventoryMovementInput = { id: string; movementType: InventoryMovementType; quantity: number; reason: string; expectedVersion: number };
const emptyReferences: InventoryReferenceData = { warehouses: [], locations: [] };

export async function getInventoryPageData(pathname: string): Promise<InventoryPageData | null> {
  if (pathname !== "/warehouse/inventory/on-hand") return null;
  const requestId = `web-inventory-${randomUUID()}`;
  const [listResponse, referenceResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/warehouse/inventory-balances?page=0&size=200&qualityStatus=ALL&warehouseCode=ALL", requestId),
    requestGuanSeqApi("/api/v1/warehouse/inventory-reference-data", requestId),
  ]);
  if (!listResponse?.ok || !referenceResponse?.ok) return { source: "unavailable", balances: [], referenceData: emptyReferences, error: "库存服务暂时不可用，请稍后刷新重试。" };
  return { source: "backend", balances: inventoryPageSchema.parse(await listResponse.json()).items, referenceData: inventoryReferenceDataSchema.parse(await referenceResponse.json()) };
}

export async function postInventoryMovement(input: InventoryMovementInput, requestId: string) {
  const response = await requestGuanSeqApi(`/api/v1/warehouse/inventory-balances/${input.id}/movements`, requestId, {
    method: "POST", body: JSON.stringify({ movementType: input.movementType, quantity: input.quantity, reason: input.reason, expectedVersion: input.expectedVersion }),
  }, 10000);
  if (!response) throw new GuanSeqApiError("库存服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "库存事务过账失败");
  return { balance: inventoryRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
