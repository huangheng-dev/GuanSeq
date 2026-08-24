import "server-only";

import { randomUUID } from "node:crypto";
import { purchaseOrderPageSchema, purchaseOrderRecordSchema, purchaseOrderReferenceDataSchema, type PurchaseOrderRecord, type PurchaseOrderReferenceData } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type ProcurementPageData = { source: "backend"; orders: PurchaseOrderRecord[]; references: PurchaseOrderReferenceData };
export type PurchaseOrderWritePayload = { supplierId: string; currency: "CNY" | "USD" | "EUR"; taxRate: number; requestedReceiptDate: string; promisedReceiptDate: string | null; buyer: string; lines: Array<{ materialId: string; orderedQuantity: number; unitPrice: number }> };
export type PurchaseOrderMutation =
  | { operation: "create"; payload: PurchaseOrderWritePayload }
  | { operation: "update"; id: string; payload: PurchaseOrderWritePayload & { expectedVersion: number } }
  | { operation: "action"; id: string; action: "SUBMIT" | "APPROVE" | "REJECT" | "RELEASE"; expectedVersion: number; comment?: string };

export async function getProcurementPageData(pathname: string): Promise<ProcurementPageData | null> {
  if (pathname !== "/procurement/orders") return null;
  const requestId = `web-purchase-list-${randomUUID()}`;
  const [ordersResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/procurement/orders?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/procurement/order-reference-data", requestId),
  ]);
  if (!ordersResponse) return null;
  if (ordersResponse && !ordersResponse.ok) await readApiError(ordersResponse, "采购订单台账加载失败");
  if (!referencesResponse) return null;
  if (referencesResponse && !referencesResponse.ok) await readApiError(referencesResponse, "采购参考数据加载失败");
  return { source: "backend", orders: purchaseOrderPageSchema.parse(await ordersResponse.json()).items, references: purchaseOrderReferenceDataSchema.parse(await referencesResponse.json()) };
}

export async function mutatePurchaseOrder(input: PurchaseOrderMutation, requestId: string) {
  const path = input.operation === "create" ? "/api/v1/procurement/orders" : input.operation === "update" ? `/api/v1/procurement/orders/${input.id}` : `/api/v1/procurement/orders/${input.id}/actions`;
  const response = await requestGuanSeqApi(path, requestId, { method: input.operation === "update" ? "PUT" : "POST", body: JSON.stringify(input.operation === "action" ? { action: input.action, expectedVersion: input.expectedVersion, comment: input.comment || null } : input.payload) });
  if (!response) throw new GuanSeqApiError("采购订单服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "采购订单服务暂时无法完成请求");
  return { order: purchaseOrderRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
