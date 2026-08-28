import "server-only";

import { equipmentMaintenanceGenerationSchema, equipmentMaintenancePlanPageSchema, equipmentMaintenancePlanSchema,
  type EquipmentMaintenanceGeneration, type EquipmentMaintenancePlan, type EquipmentMaintenancePlanAction,
  type EquipmentMaintenancePlanPage, type EquipmentWorkOrderPriority } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type EquipmentMaintenancePlanMutation =
  | { operation: "createPlan"; planCode: string; name: string; workType: "INSPECTION" | "PREVENTIVE_MAINTENANCE";
      assetId: string; description: string; priority: EquipmentWorkOrderPriority; intervalDays: number; leadDays: number;
      firstDueDate: string; plannedStartTime: string; dueTime: string; assignee: string; reason: string;
      assetExpectedVersion: number }
  | { operation: "planAction"; id: string; action: EquipmentMaintenancePlanAction; reason: string; expectedVersion: number }
  | { operation: "generateDue"; asOfDate: string; reason: string };

export type EquipmentMaintenancePlanPageData =
  | { source: "backend"; page: EquipmentMaintenancePlanPage; requestId: string }
  | { source: "unavailable"; page: null; status: number; message: string; requestId: string };

async function responseMessage(response: Response, fallback: string) {
  try { return ((await response.json()) as { message?: string }).message ?? fallback; }
  catch { return fallback; }
}

export async function loadEquipmentMaintenancePlanPage(requestId: string): Promise<EquipmentMaintenancePlanPageData> {
  const response = await requestGuanSeqApi("/api/v1/equipment/maintenance-plans?page=0&size=200&status=ALL", requestId);
  if (!response) return { source: "unavailable", page: null, status: 503, message: "周期维护计划服务暂时不可用", requestId };
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  if (!response.ok) return { source: "unavailable", page: null, status: response.status,
    message: await responseMessage(response, response.status === 403 ? "当前角色无权读取周期维护计划" : "周期维护计划加载失败"),
    requestId: responseRequestId };
  return { source: "backend", page: equipmentMaintenancePlanPageSchema.parse(await response.json()), requestId: responseRequestId };
}

export async function loadEquipmentMaintenancePlan(id: string, requestId: string): Promise<{ plan: EquipmentMaintenancePlan; requestId: string }> {
  const response = await requestGuanSeqApi(`/api/v1/equipment/maintenance-plans/${id}`, requestId);
  if (!response) throw new GuanSeqApiError("周期维护计划服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "周期维护模板详情加载失败");
  return { plan: equipmentMaintenancePlanSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function mutateEquipmentMaintenancePlan(input: EquipmentMaintenancePlanMutation, requestId: string): Promise<{
  plan?: EquipmentMaintenancePlan; generation?: EquipmentMaintenanceGeneration; requestId: string;
}> {
  const path = input.operation === "createPlan" ? "/api/v1/equipment/maintenance-plans"
    : input.operation === "planAction" ? `/api/v1/equipment/maintenance-plans/${input.id}/actions`
    : "/api/v1/equipment/maintenance-plans/generate";
  const body = input.operation === "createPlan" ? {
    planCode: input.planCode, name: input.name, workType: input.workType, assetId: input.assetId,
    description: input.description, priority: input.priority, intervalDays: input.intervalDays, leadDays: input.leadDays,
    firstDueDate: input.firstDueDate, plannedStartTime: input.plannedStartTime, dueTime: input.dueTime,
    assignee: input.assignee, reason: input.reason, assetExpectedVersion: input.assetExpectedVersion,
  } : input.operation === "planAction" ? {
    action: input.action, reason: input.reason, expectedVersion: input.expectedVersion,
  } : { asOfDate: input.asOfDate, reason: input.reason };
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) }, 15000);
  if (!response) throw new GuanSeqApiError("周期维护计划服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "周期维护计划操作失败");
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  const payload = await response.json();
  return input.operation === "generateDue"
    ? { generation: equipmentMaintenanceGenerationSchema.parse(payload), requestId: responseRequestId }
    : { plan: equipmentMaintenancePlanSchema.parse(payload), requestId: responseRequestId };
}
