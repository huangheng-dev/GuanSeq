import "server-only";

import { randomUUID } from "node:crypto";
import { z } from "zod";
import { orderProfitPageSchema, orderProfitRecordSchema, orderProfitReferenceDataSchema, orderProfitResettleRequestSchema, type OrderProfitRecord } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type OrderProfitPageData = {
  source: "backend";
  settlements: OrderProfitRecord[];
  references: ReturnType<typeof orderProfitReferenceDataSchema.parse>;
};

export type SettleOrderProfitPayload = {
  salesOrderId: string;
};

export type ResettleOrderProfitPayload = {
  salesOrderId: string;
  reason: string;
  settlementDate?: string | null;
  expectedVersion?: number | null;
};

export async function getOrderProfitPageData(pathname: string): Promise<OrderProfitPageData | null> {
  if (pathname !== "/finance/order-profit") return null;
  const requestId = `web-order-profit-list-${randomUUID()}`;
  const [settlementsResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/finance/order-profits?page=0&size=100&costStatus=ALL", requestId),
    requestGuanSeqApi("/api/v1/finance/order-profit-reference-data", requestId),
  ]);
  if (!settlementsResponse) return null;
  if (settlementsResponse && !settlementsResponse.ok) await readApiError(settlementsResponse, "订单利润台账加载失败");
  if (!referencesResponse) return null;
  if (referencesResponse && !referencesResponse.ok) await readApiError(referencesResponse, "订单利润参考数据加载失败");
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

export async function resettleOrderProfit(payload: ResettleOrderProfitPayload, requestId: string) {
  const body = orderProfitResettleRequestSchema.parse({
    reason: payload.reason,
    settlementDate: payload.settlementDate ?? null,
    expectedVersion: payload.expectedVersion ?? null,
  });
  const response = await requestGuanSeqApi(`/api/v1/finance/order-profits/${payload.salesOrderId}/resettle`, requestId, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response) throw new GuanSeqApiError("订单利润服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "订单利润重算暂时无法完成");
  return { settlement: orderProfitRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function getOrderProfitHistory(salesOrderId: string, requestId: string): Promise<OrderProfitRecord[]> {
  const response = await requestGuanSeqApi(`/api/v1/finance/order-profits/${salesOrderId}/history`, requestId);
  if (!response) throw new GuanSeqApiError("订单利润历史暂时不可用", 503);
  if (!response.ok) await readApiError(response, "订单利润历史加载失败");
	return z.array(orderProfitRecordSchema).parse(await response.json());
}

