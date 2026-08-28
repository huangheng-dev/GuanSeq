import "server-only";

import { randomUUID } from "node:crypto";

import {
  equipmentWorkOrderPageSchema,
  equipmentWorkOrderSchema,
  equipmentMaintenanceCostMutationResultSchema,
  type EquipmentAsset,
  type EquipmentWorkOrder,
  type EquipmentWorkOrderAction,
  type EquipmentWorkOrderOutcome,
  type EquipmentWorkOrderPage,
  type EquipmentWorkOrderPriority,
  type EquipmentWorkType,
} from "@/lib/contracts";
import { loadEquipmentAssetPage } from "@/services/equipment-asset-server-service";
import { loadEquipmentMaintenancePlanPage, type EquipmentMaintenancePlanPageData } from "@/services/equipment-maintenance-plan-server-service";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type EquipmentWorkOrderMutation =
  | { operation: "create"; assetId: string; workType: EquipmentWorkType; title: string; description: string;
      priority: EquipmentWorkOrderPriority; plannedStartAt: string; dueAt: string; assignee: string; reason: string;
      assetExpectedVersion: number }
  | { operation: "action"; id: string; action: EquipmentWorkOrderAction; reason: string; expectedVersion: number;
      assetExpectedVersion: number; outcome?: EquipmentWorkOrderOutcome | null; completionNotes?: string | null };

export type EquipmentMaintenanceCostMutation =
  | { operation: "issueSpare"; id: string; sparePartId: string; warehouseId: string; quantity: number; reason: string; expectedVersion: number }
  | { operation: "returnSpare"; id: string; issueTransactionId: string; locationId: string; quantity: number; reason: string; expectedVersion: number }
  | { operation: "recordLabor"; id: string; technicianName: string; hours: number; hourlyRate: number; currency: string; reason: string; expectedVersion: number }
  | { operation: "reverseLabor"; id: string; entryId: string; reason: string; expectedVersion: number };

export type EquipmentWorkOrderPageData =
  | { source: "backend"; page: EquipmentWorkOrderPage; assets: EquipmentAsset[]; requestId: string; maintenancePlans: EquipmentMaintenancePlanPageData | null }
  | { source: "unavailable"; page: null; assets: EquipmentAsset[]; status: number; message: string; requestId: string; maintenancePlans: EquipmentMaintenancePlanPageData | null };

async function responseMessage(response: Response, fallback: string) {
  try { return ((await response.json()) as { message?: string }).message ?? fallback; }
  catch { return fallback; }
}

export async function loadEquipmentWorkOrderPage(requestId: string): Promise<EquipmentWorkOrderPageData> {
  const [response, assetResult] = await Promise.all([
    requestGuanSeqApi("/api/v1/equipment/work-orders?page=0&size=200&type=ALL&status=ALL", requestId),
    loadEquipmentAssetPage(`${requestId}-assets`),
  ]);
  if (!response) return { source: "unavailable", page: null, assets: [], status: 503, message: "设备运维服务暂时不可用", requestId, maintenancePlans: null };
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  if (!response.ok) return { source: "unavailable", page: null, assets: [], status: response.status,
    message: await responseMessage(response, response.status === 403 ? "当前角色无权读取设备运维工单" : "设备运维工单加载失败"), requestId: responseRequestId, maintenancePlans: null };
  if (assetResult.source === "unavailable") return { source: "unavailable", page: null, assets: [], status: assetResult.status,
    message: "设备台账不可用，无法安全装配运维工单", requestId: assetResult.requestId, maintenancePlans: null };
  return { source: "backend", page: equipmentWorkOrderPageSchema.parse(await response.json()), assets: assetResult.page.items,
    requestId: responseRequestId, maintenancePlans: null };
}

export async function loadEquipmentWorkOrder(id: string, requestId: string): Promise<{ workOrder: EquipmentWorkOrder; requestId: string }> {
  const response = await requestGuanSeqApi(`/api/v1/equipment/work-orders/${id}`, requestId);
  if (!response) throw new GuanSeqApiError("设备运维服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "设备运维工单详情加载失败");
  return { workOrder: equipmentWorkOrderSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function getEquipmentWorkOrderPageData(pathname: string): Promise<EquipmentWorkOrderPageData | null> {
  if (!new Set(["/equipment/inspections", "/equipment/maintenance", "/equipment/work-orders"]).has(pathname)) return null;
  const requestId = `web-equipment-work-orders-${randomUUID()}`;
  if (pathname !== "/equipment/maintenance") return loadEquipmentWorkOrderPage(requestId);
  const [workOrders, maintenancePlans] = await Promise.all([
    loadEquipmentWorkOrderPage(requestId), loadEquipmentMaintenancePlanPage(`${requestId}-plans`),
  ]);
  return { ...workOrders, maintenancePlans };
}

export async function mutateEquipmentWorkOrder(input: EquipmentWorkOrderMutation, requestId: string) {
  const path = input.operation === "create" ? "/api/v1/equipment/work-orders" : `/api/v1/equipment/work-orders/${input.id}/actions`;
  const body = input.operation === "create" ? {
    assetId: input.assetId, workType: input.workType, title: input.title, description: input.description,
    priority: input.priority, plannedStartAt: input.plannedStartAt, dueAt: input.dueAt, assignee: input.assignee,
    reason: input.reason, assetExpectedVersion: input.assetExpectedVersion,
  } : {
    action: input.action, reason: input.reason, expectedVersion: input.expectedVersion,
    assetExpectedVersion: input.assetExpectedVersion, outcome: input.outcome ?? null,
    completionNotes: input.completionNotes ?? null,
  };
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) }, 10000);
  if (!response) throw new GuanSeqApiError("设备运维服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "设备运维操作失败");
  return { workOrder: equipmentWorkOrderSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function mutateEquipmentMaintenanceCost(input: EquipmentMaintenanceCostMutation, requestId: string) {
  const path = input.operation === "issueSpare" ? `/api/v1/equipment/work-orders/${input.id}/spare-issues`
    : input.operation === "returnSpare" ? `/api/v1/equipment/work-orders/${input.id}/spare-returns`
    : input.operation === "recordLabor" ? `/api/v1/equipment/work-orders/${input.id}/labor-entries`
    : `/api/v1/equipment/work-orders/${input.id}/labor-entries/${input.entryId}/reversals`;
  const body = input.operation === "issueSpare" ? { sparePartId: input.sparePartId, warehouseId: input.warehouseId,
    quantity: input.quantity, reason: input.reason, expectedVersion: input.expectedVersion }
    : input.operation === "returnSpare" ? { issueTransactionId: input.issueTransactionId, locationId: input.locationId,
      quantity: input.quantity, reason: input.reason, expectedVersion: input.expectedVersion }
    : input.operation === "recordLabor" ? { technicianName: input.technicianName, hours: input.hours,
      hourlyRate: input.hourlyRate, currency: input.currency, reason: input.reason, expectedVersion: input.expectedVersion }
    : { reason: input.reason, expectedVersion: input.expectedVersion };
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) }, 10000);
  if (!response) throw new GuanSeqApiError("维修成本服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "维修备件或人工成本操作失败");
  return { result: equipmentMaintenanceCostMutationResultSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId };
}
