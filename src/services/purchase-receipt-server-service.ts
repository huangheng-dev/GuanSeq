import "server-only";

import { randomUUID } from "node:crypto";
import { purchaseReceiptPageSchema, purchaseReceiptRecordSchema, purchaseReceiptReferenceDataSchema, type PurchaseReceiptRecord } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type PurchaseReceiptPageData = { source: "backend"; receipts: PurchaseReceiptRecord[]; references: ReturnType<typeof purchaseReceiptReferenceDataSchema.parse> };
export type CreatePurchaseReceiptPayload = {
  purchaseOrderId: string;
  warehouseId: string;
  locationId: string;
  note?: string | null;
  lines: Array<{ orderLineId: string; receivedQuantity: number; lotNumber: string }>;
};

export async function getPurchaseReceiptPageData(pathname: string): Promise<PurchaseReceiptPageData | null> {
  if (pathname !== "/procurement/receipts") return null;
  const requestId = `web-purchase-receipt-list-${randomUUID()}`;
  const [receiptsResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/procurement/receipts?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/procurement/receipt-reference-data", requestId),
  ]);
  if (!receiptsResponse) return null;
  if (receiptsResponse && !receiptsResponse.ok) await readApiError(receiptsResponse, "采购收货台账加载失败");
  if (!referencesResponse) return null;
  if (referencesResponse && !referencesResponse.ok) await readApiError(referencesResponse, "采购收货参考数据加载失败");
  return {
    source: "backend",
    receipts: purchaseReceiptPageSchema.parse(await receiptsResponse.json()).items,
    references: purchaseReceiptReferenceDataSchema.parse(await referencesResponse.json()),
  };
}

export async function createPurchaseReceipt(payload: CreatePurchaseReceiptPayload, requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/procurement/receipts", requestId, { method: "POST", body: JSON.stringify(payload) });
  if (!response) throw new GuanSeqApiError("采购收货服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "采购收货服务暂时无法完成请求");
  return { receipt: purchaseReceiptRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
