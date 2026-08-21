import "server-only";

import { randomUUID } from "node:crypto";
import { salesShipmentPageSchema, salesShipmentRecordSchema, salesShipmentReferenceDataSchema, type SalesShipmentRecord } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type SalesShipmentPageData = {
  source: "backend";
  shipments: SalesShipmentRecord[];
  references: ReturnType<typeof salesShipmentReferenceDataSchema.parse>;
};

export type CreateSalesShipmentPayload = {
  salesOrderId: string;
  warehouseId: string;
  plannedShippingDate: string;
  note?: string | null;
  lines: Array<{ orderLineId: string; shippedQuantity: number }>;
};

export async function getSalesShipmentPageData(pathname: string): Promise<SalesShipmentPageData | null> {
  if (pathname !== "/sales/deliveries/pending") return null;
  const requestId = `web-sales-shipment-list-${randomUUID()}`;
  const [shipmentsResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/sales/shipments?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/sales/shipment-reference-data", requestId),
  ]);
  if (!shipmentsResponse?.ok || !referencesResponse?.ok) return null;
  return {
    source: "backend",
    shipments: salesShipmentPageSchema.parse(await shipmentsResponse.json()).items,
    references: salesShipmentReferenceDataSchema.parse(await referencesResponse.json()),
  };
}

export async function createSalesShipment(payload: CreateSalesShipmentPayload, requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/sales/shipments", requestId, { method: "POST", body: JSON.stringify(payload) });
  if (!response) throw new GuanSeqApiError("销售发货服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "销售发货服务暂时无法完成请求");
  return { shipment: salesShipmentRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}