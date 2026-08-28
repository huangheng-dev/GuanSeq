import "server-only";

import { randomUUID } from "node:crypto";

import { equipmentAlertPageSchema, equipmentAlertRulePageSchema, equipmentAlertRuleSchema,
  equipmentAlertSchema, equipmentTelemetryConnectionPageSchema, equipmentTelemetryConnectionSchema, equipmentWorkOrderPageSchema,
  type EquipmentAlert, type EquipmentAlertPage, type EquipmentAlertRule, type EquipmentAlertRulePage,
  type EquipmentAlertRuleType, type EquipmentAlertSeverity, type EquipmentTelemetryConnectionPage,
  type EquipmentWorkOrderPage } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type EquipmentAlertMutation =
  | { operation: "createRule"; ruleCode: string; name: string; connectionId: string; pointId?: string | null;
      ruleType: EquipmentAlertRuleType; thresholdValue?: number | null; severity: EquipmentAlertSeverity;
      defaultAssignee: string; reason: string }
  | { operation: "actOnRule"; id: string; action: "ACTIVATE" | "PAUSE"; reason: string; expectedVersion: number }
  | { operation: "actOnAlert"; id: string; action: "ACKNOWLEDGE" | "START_PROCESSING" | "RESOLVE" | "CLOSE" | "LINK_REPAIR";
      reason: string; expectedVersion: number; assignee?: string | null; resolutionNotes?: string | null; workOrderId?: string | null };

export type EquipmentAlertPageData =
  | { source: "backend"; alertPage: EquipmentAlertPage; rulePage: EquipmentAlertRulePage;
      connections: EquipmentTelemetryConnectionPage; workOrders: EquipmentWorkOrderPage; requestId: string }
  | { source: "unavailable"; alertPage: null; rulePage: null; connections: null; workOrders: null;
      status: number; message: string; requestId: string };

async function message(response: Response | null, fallback: string) {
  if (!response) return fallback;
  try { return ((await response.json()) as { message?: string }).message ?? fallback; } catch { return fallback; }
}

export async function loadEquipmentAlertPage(requestId: string): Promise<EquipmentAlertPageData> {
  const [alerts, rules, connections, workOrders] = await Promise.all([
    requestGuanSeqApi("/api/v1/equipment/alerts?page=0&size=100&status=ALL&severity=ALL", requestId),
    requestGuanSeqApi("/api/v1/equipment/alert-rules?page=0&size=100&status=ALL", `${requestId}-rules`),
    requestGuanSeqApi("/api/v1/equipment/telemetry-connections?page=0&size=100", `${requestId}-connections`),
    requestGuanSeqApi("/api/v1/equipment/work-orders?page=0&size=200&type=REPAIR&status=ALL", `${requestId}-repairs`),
  ]);
  const failed = [alerts, rules, connections, workOrders].find((response) => !response?.ok) ?? null;
  if (failed || !alerts || !rules || !connections || !workOrders) return { source: "unavailable", alertPage: null,
    rulePage: null, connections: null, workOrders: null, status: failed?.status ?? 503,
    message: await message(failed, "设备报警服务暂时不可用"), requestId: failed?.headers.get("X-Request-Id") ?? requestId };
  const connectionPage = equipmentTelemetryConnectionPageSchema.parse(await connections.json());
  const detailedConnections = connectionPage.canManage ? await Promise.all(connectionPage.items.map(async (connection) => {
    const response = await requestGuanSeqApi(`/api/v1/equipment/telemetry-connections/${connection.id}`,
      `${requestId}-connection-${connection.id}`);
    return response?.ok ? equipmentTelemetryConnectionSchema.parse(await response.json()) : connection;
  })) : connectionPage.items;
  return { source: "backend", alertPage: equipmentAlertPageSchema.parse(await alerts.json()),
    rulePage: equipmentAlertRulePageSchema.parse(await rules.json()), connections: { ...connectionPage, items: detailedConnections },
    workOrders: equipmentWorkOrderPageSchema.parse(await workOrders.json()),
    requestId: alerts.headers.get("X-Request-Id") ?? requestId };
}

export async function getEquipmentAlertPageData(pathname: string): Promise<EquipmentAlertPageData | null> {
  if (pathname !== "/equipment/alerts") return null;
  return loadEquipmentAlertPage(`web-equipment-alert-${randomUUID()}`);
}

export async function loadEquipmentAlert(id: string, requestId: string): Promise<{ alert: EquipmentAlert; requestId: string }> {
  const response = await requestGuanSeqApi(`/api/v1/equipment/alerts/${id}`, requestId);
  if (!response) throw new GuanSeqApiError("设备报警服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "设备报警详情加载失败");
  return { alert: equipmentAlertSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function mutateEquipmentAlert(input: EquipmentAlertMutation, requestId: string): Promise<{
  alert?: EquipmentAlert; rule?: EquipmentAlertRule; requestId: string;
}> {
  const create = input.operation === "createRule";
  const ruleAction = input.operation === "actOnRule";
  const path = create ? "/api/v1/equipment/alert-rules" : ruleAction
    ? `/api/v1/equipment/alert-rules/${input.id}/actions` : `/api/v1/equipment/alerts/${input.id}/actions`;
  const body = create ? { ruleCode: input.ruleCode, name: input.name, connectionId: input.connectionId,
    pointId: input.pointId ?? null, ruleType: input.ruleType, thresholdValue: input.thresholdValue ?? null,
    severity: input.severity, defaultAssignee: input.defaultAssignee, reason: input.reason }
    : { action: input.action, reason: input.reason, expectedVersion: input.expectedVersion,
      ...(input.operation === "actOnAlert" ? { assignee: input.assignee ?? null,
        resolutionNotes: input.resolutionNotes ?? null, workOrderId: input.workOrderId ?? null } : {}) };
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("设备报警服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "设备报警操作失败");
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  return create || ruleAction ? { rule: equipmentAlertRuleSchema.parse(await response.json()), requestId: responseRequestId }
    : { alert: equipmentAlertSchema.parse(await response.json()), requestId: responseRequestId };
}
