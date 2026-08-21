import "server-only";

import { randomUUID } from "node:crypto";
import {
  finalInspectionPageSchema,
  finalInspectionRecordSchema,
  inventoryReferenceDataSchema,
  productionOrderPageSchema,
  productionWorkReportPageSchema,
  productionWorkReportRecordSchema,
  type FinalInspectionRecord,
  type InventoryReferenceData,
  type ProductionOrderRecord,
  type ProductionWorkReportRecord,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type ProductionExecutionPageData = {
  source: "backend";
  reports: ProductionWorkReportRecord[];
  orders: ProductionOrderRecord[];
  inventoryReferences: InventoryReferenceData;
};

export type FinalInspectionPageData = { source: "backend"; inspections: FinalInspectionRecord[] };

export type ProductionExecutionMutation =
  | { operation: "report"; orderId: string; quantity: number; shiftName: string; operatorName: string; note: string | null; expectedOrderVersion: number }
  | { operation: "settle"; id: string; warehouseId: string | null; locationId: string | null; lotNumber: string | null; expectedVersion: number };

export type FinalInspectionMutation = {
  id: string;
  acceptedQuantity: number;
  rejectedQuantity: number;
  inspector: string;
  defectDescription: string | null;
  conclusion: string;
  expectedVersion: number;
};

export async function getProductionExecutionPageData(pathname: string): Promise<ProductionExecutionPageData | null> {
  if (pathname !== "/production/reporting/reports") return null;
  const requestId = `web-production-execution-${randomUUID()}`;
  const [reportsResponse, ordersResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/production/work-reports?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/production/orders?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/warehouse/inventory-reference-data", requestId),
  ]);
  if (!reportsResponse?.ok || !ordersResponse?.ok || !referencesResponse?.ok) return null;
  return {
    source: "backend",
    reports: productionWorkReportPageSchema.parse(await reportsResponse.json()).items,
    orders: productionOrderPageSchema.parse(await ordersResponse.json()).items,
    inventoryReferences: inventoryReferenceDataSchema.parse(await referencesResponse.json()),
  };
}

export async function getFinalInspectionPageData(pathname: string): Promise<FinalInspectionPageData | null> {
  if (pathname !== "/quality/final") return null;
  const response = await requestGuanSeqApi("/api/v1/quality/final-inspections?page=0&size=100&status=ALL", `web-final-quality-${randomUUID()}`);
  if (!response?.ok) return null;
  return { source: "backend", inspections: finalInspectionPageSchema.parse(await response.json()).items };
}

export async function mutateProductionExecution(input: ProductionExecutionMutation, requestId: string) {
  const path = input.operation === "report" ? "/api/v1/production/work-reports" : `/api/v1/production/work-reports/${input.id}/settle`;
  const body = input.operation === "report"
    ? { orderId: input.orderId, quantity: input.quantity, shiftName: input.shiftName, operatorName: input.operatorName, note: input.note, expectedOrderVersion: input.expectedOrderVersion }
    : { warehouseId: input.warehouseId, locationId: input.locationId, lotNumber: input.lotNumber, expectedVersion: input.expectedVersion };
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("生产执行服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "生产执行服务暂时无法完成请求");
  return { report: productionWorkReportRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function completeFinalInspection(input: FinalInspectionMutation, requestId: string) {
  const response = await requestGuanSeqApi(`/api/v1/quality/final-inspections/${input.id}/complete`, requestId, {
    method: "POST",
    body: JSON.stringify({ acceptedQuantity: input.acceptedQuantity, rejectedQuantity: input.rejectedQuantity, inspector: input.inspector, defectDescription: input.defectDescription, conclusion: input.conclusion, expectedVersion: input.expectedVersion }),
  });
  if (!response) throw new GuanSeqApiError("质量检验服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "质量检验服务暂时无法完成请求");
  return { inspection: finalInspectionRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
