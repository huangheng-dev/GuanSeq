import "server-only";

import { randomUUID } from "node:crypto";
import { salesReturnPageSchema, salesReturnRecordSchema, salesReturnReferenceDataSchema } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type SalesReturnPageData = {
  source: "backend";
  page: ReturnType<typeof salesReturnPageSchema.parse>;
  references: ReturnType<typeof salesReturnReferenceDataSchema.parse>;
};

export type SalesReturnMutation =
  | { operation: "create"; salesOrderId: string; expectedOrderVersion: number; returnDate: string; reason: string; note?: string | null; lines: Array<{ orderLineId: string; returnQuantity: number }> }
  | { operation: "action"; id: string; action: "CANCEL" | "RECEIVE" | "INSPECT" | "REVERSE_RECEIPT"; expectedVersion: number; reason: string; warehouseId?: string | null; locationId?: string | null; lines?: Array<{ returnLineId: string; lotNumber?: string | null; acceptedQuantity?: number | null; rejectedQuantity?: number | null }> };

export async function getSalesReturnPageData(pathname: string): Promise<SalesReturnPageData | null> {
  if (pathname !== "/sales/returns") return null;
  const requestId = `web-sales-return-list-${randomUUID()}`;
  const [pageResponse, referenceResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/sales/returns?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/sales/return-reference-data", requestId),
  ]);
  if (!pageResponse || !referenceResponse) return null;
  if (!pageResponse.ok) await readApiError(pageResponse, "销售退货台账加载失败");
  if (!referenceResponse.ok) await readApiError(referenceResponse, "销售退货引用数据加载失败");
  return { source: "backend", page: salesReturnPageSchema.parse(await pageResponse.json()), references: salesReturnReferenceDataSchema.parse(await referenceResponse.json()) };
}

export async function mutateSalesReturn(input: SalesReturnMutation, requestId: string) {
  const path = input.operation === "create" ? "/api/v1/sales/returns" : `/api/v1/sales/returns/${input.id}/actions`;
  const body: Record<string, unknown> = { ...input };
  delete body.operation;
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("销售退货服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "销售退货动作未完成");
  return { record: salesReturnRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
