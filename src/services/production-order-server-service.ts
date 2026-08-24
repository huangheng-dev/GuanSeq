import "server-only";

import { randomUUID } from "node:crypto";
import { productionOrderPageSchema, productionOrderRecordSchema, productionOrderReferenceDataSchema, type ProductionOrderRecord, type ProductionOrderReferenceData } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type ProductionOrderPageData = { source: "backend"; orders: ProductionOrderRecord[]; references: ProductionOrderReferenceData };
export type ProductionOrderWritePayload = { materialId: string; plannedQuantity: number; plannedStartDate: string; plannedReceiptDate: string; workshop: string; owner: string; sourceType: "MANUAL" | "MRP" | "SALES_ORDER"; sourceId: string | null; sourceNumber: string | null };
export type ProductionOrderMutation =
  | { operation: "create"; payload: ProductionOrderWritePayload }
  | { operation: "update"; id: string; payload: ProductionOrderWritePayload & { expectedVersion: number } }
  | { operation: "action"; id: string; action: "RELEASE" | "START" | "CANCEL"; expectedVersion: number; comment?: string };

export async function getProductionOrderPageData(pathname: string): Promise<ProductionOrderPageData | null> {
  if (pathname !== "/production/orders/list") return null;
  const requestId = `web-production-list-${randomUUID()}`;
  const [ordersResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/production/orders?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/production/order-reference-data", requestId),
  ]);
  if (!ordersResponse) return null;
  if (ordersResponse && !ordersResponse.ok) await readApiError(ordersResponse, "生产订单台账加载失败");
  if (!referencesResponse) return null;
  if (referencesResponse && !referencesResponse.ok) await readApiError(referencesResponse, "生产订单参考数据加载失败");
  return { source: "backend", orders: productionOrderPageSchema.parse(await ordersResponse.json()).items, references: productionOrderReferenceDataSchema.parse(await referencesResponse.json()) };
}

export async function mutateProductionOrder(input: ProductionOrderMutation, requestId: string) {
  const path = input.operation === "create" ? "/api/v1/production/orders" : input.operation === "update" ? `/api/v1/production/orders/${input.id}` : `/api/v1/production/orders/${input.id}/actions`;
  const response = await requestGuanSeqApi(path, requestId, { method: input.operation === "update" ? "PUT" : "POST", body: JSON.stringify(input.operation === "action" ? { action: input.action, expectedVersion: input.expectedVersion, comment: input.comment || null } : input.payload) });
  if (!response) throw new GuanSeqApiError("生产订单服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "生产订单服务暂时无法完成请求");
  return { order: productionOrderRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
