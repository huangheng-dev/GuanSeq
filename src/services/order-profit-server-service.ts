import "server-only";

import { randomUUID } from "node:crypto";
import { orderProfitPageSchema, orderProfitRecordSchema, orderProfitReferenceDataSchema, type OrderProfitRecord } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type OrderProfitPageData = {
  source: "backend";
  settlements: OrderProfitRecord[];
  references: ReturnType<typeof orderProfitReferenceDataSchema.parse>;
};

export type SettleOrderProfitPayload = {
  salesOrderId: string;
};

export async function getOrderProfitPageData(pathname: string): Promise<OrderProfitPageData | null> {
  if (pathname !== "/finance/order-profit") return null;
  const requestId = `web-order-profit-list-${randomUUID()}`;
  const [settlementsResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/finance/order-profits?page=0&size=100&costStatus=ALL", requestId),
    requestGuanSeqApi("/api/v1/finance/order-profit-reference-data", requestId),
  ]);
  if (!settlementsResponse?.ok || !referencesResponse?.ok) return null;
  return {
    source: "backend",
    settlements: orderProfitPageSchema.parse(await settlementsResponse.json()).items,
    references: orderProfitReferenceDataSchema.parse(await referencesResponse.json()),
  };
}

export async function settleOrderProfit(payload: SettleOrderProfitPayload, requestId: string) {
  const response = await requestGuanSeqApi(`/api/v1/finance/order-profits/${payload.salesOrderId}/settle`, requestId, { method: "POST" });
  if (!response) throw new GuanSeqApiError("订单利润服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "订单利润结算暂时无法完成");
  return { settlement: orderProfitRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
