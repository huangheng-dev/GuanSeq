import "server-only";

import { randomUUID } from "node:crypto";
import { purchaseReturnPageSchema, purchaseReturnRecordSchema, purchaseReturnReferenceDataSchema } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type PurchaseReturnPageData = {
  source: "backend";
  page: ReturnType<typeof purchaseReturnPageSchema.parse>;
  references: ReturnType<typeof purchaseReturnReferenceDataSchema.parse>;
};

export type PurchaseReturnMutation =
  | { operation: "create"; purchaseOrderId: string; expectedOrderVersion: number; returnDate: string; reason: string; note?: string | null; lines: Array<{ purchaseReceiptLineId: string; qualityStatus: "AVAILABLE" | "BLOCKED"; returnQuantity: number }> }
  | { operation: "action"; id: string; action: "CANCEL" | "SHIP" | "REVERSE"; expectedVersion: number; reason: string };

export async function getPurchaseReturnPageData(pathname: string): Promise<PurchaseReturnPageData | null> {
  if (pathname !== "/procurement/returns") return null;
  const requestId = `web-purchase-return-list-${randomUUID()}`;
  const [pageResponse, referenceResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/procurement/returns?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/procurement/return-reference-data", requestId),
  ]);
  if (!pageResponse || !referenceResponse) return null;
  if (!pageResponse.ok) await readApiError(pageResponse, "采购退货台账加载失败");
  if (!referenceResponse.ok) await readApiError(referenceResponse, "采购退货引用数据加载失败");
  return { source: "backend", page: purchaseReturnPageSchema.parse(await pageResponse.json()), references: purchaseReturnReferenceDataSchema.parse(await referenceResponse.json()) };
}

export async function mutatePurchaseReturn(input: PurchaseReturnMutation, requestId: string) {
  const path = input.operation === "create" ? "/api/v1/procurement/returns" : `/api/v1/procurement/returns/${input.id}/actions`;
  const body: Record<string, unknown> = { ...input };
  delete body.operation;
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("采购退货服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "采购退货动作未完成");
  return { record: purchaseReturnRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
