import "server-only";

import { randomUUID } from "node:crypto";

import {
  salesOrderPageSchema,
  salesOrderRecordSchema,
  salesOrderReferenceDataSchema,
  type SalesOrderRecord,
  type SalesOrderReferenceData,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type SalesOrderPageData = {
  source: "backend";
  orders: SalesOrderRecord[];
  references: SalesOrderReferenceData;
};

export async function getSalesOrderPageData(pathname: string): Promise<SalesOrderPageData | null> {
  if (pathname !== "/sales/orders/list") return null;
  const requestId = `web-sales-list-${randomUUID()}`;
  const [ordersResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/sales/orders?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/sales/order-reference-data", requestId),
  ]);
  if (!ordersResponse?.ok || !referencesResponse?.ok) return null;
  return {
    source: "backend",
    orders: salesOrderPageSchema.parse(await ordersResponse.json()).items,
    references: salesOrderReferenceDataSchema.parse(await referencesResponse.json()),
  };
}

export type SalesOrderMutation =
  | { operation: "create"; payload: SalesOrderWritePayload }
  | { operation: "update"; id: string; payload: SalesOrderWritePayload & { expectedVersion: number } }
  | { operation: "action"; id: string; action: "SUBMIT" | "APPROVE" | "REJECT" | "RELEASE"; expectedVersion: number; comment?: string };

export type SalesOrderWritePayload = {
  customerId: string;
  currency: "CNY" | "USD" | "EUR";
  taxRate: number;
  requestedDeliveryDate: string;
  promisedDeliveryDate: string | null;
  owner: string;
  lines: Array<{ materialId: string; quantity: number; unitPrice: number }>;
};

export async function mutateSalesOrder(input: SalesOrderMutation, requestId: string) {
  const path = input.operation === "create" ? "/api/v1/sales/orders" : input.operation === "update" ? `/api/v1/sales/orders/${input.id}` : `/api/v1/sales/orders/${input.id}/actions`;
  const method = input.operation === "update" ? "PUT" : "POST";
  const body = input.operation === "action"
    ? { action: input.action, expectedVersion: input.expectedVersion, comment: input.comment || null }
    : input.payload;
  const response = await requestGuanSeqApi(path, requestId, { method, body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("销售订单服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "销售订单服务暂时无法完成请求");
  return {
    order: salesOrderRecordSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}
