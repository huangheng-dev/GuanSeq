import { equipmentAlertPageSchema, equipmentAlertRulePageSchema, equipmentAlertRuleSchema, equipmentAlertSchema,
  equipmentTelemetryConnectionPageSchema, equipmentWorkOrderPageSchema, type EquipmentAlert,
  type EquipmentAlertRule } from "@/lib/contracts";
import type { EquipmentAlertMutation, EquipmentAlertPageData } from "@/services/equipment-alert-server-service";

export class EquipmentAlertClientError extends Error {
  constructor(message: string, readonly status: number, readonly requestId?: string) { super(message); }
}

async function payload(response: Response) { return await response.json().catch(() => null) as Record<string, unknown> | null; }
function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new EquipmentAlertClientError(typeof body?.message === "string" ? body.message : fallback, response.status,
    typeof body?.requestId === "string" ? body.requestId : response.headers.get("X-Request-Id") ?? undefined);
}

export async function refreshEquipmentAlertPage(): Promise<Extract<EquipmentAlertPageData, { source: "backend" }>> {
  const response = await fetch("/api/equipment/alerts", { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.data || typeof body.data !== "object") throw failure(response, body, "设备报警刷新失败");
  const data = body.data as Record<string, unknown>;
  return { source: "backend", alertPage: equipmentAlertPageSchema.parse(data.alertPage),
    rulePage: equipmentAlertRulePageSchema.parse(data.rulePage),
    connections: equipmentTelemetryConnectionPageSchema.parse(data.connections),
    workOrders: equipmentWorkOrderPageSchema.parse(data.workOrders),
    requestId: typeof data.requestId === "string" ? data.requestId : "web-equipment-alert" };
}

export async function loadEquipmentAlertDetail(id: string): Promise<EquipmentAlert> {
  const response = await fetch(`/api/equipment/alerts?id=${encodeURIComponent(id)}`, { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.alert) throw failure(response, body, "设备报警详情加载失败");
  return equipmentAlertSchema.parse(body.alert);
}

export async function submitEquipmentAlertMutation(input: EquipmentAlertMutation): Promise<EquipmentAlert | EquipmentAlertRule> {
  const response = await fetch("/api/equipment/alerts", { method: "POST", headers: {
    "Content-Type": "application/json", "X-Request-Id": `web-equipment-alert-${crypto.randomUUID()}` }, body: JSON.stringify(input) });
  const body = await payload(response);
  if (!response.ok) throw failure(response, body, "设备报警操作失败");
  if (body?.alert) return equipmentAlertSchema.parse(body.alert);
  if (body?.rule) return equipmentAlertRuleSchema.parse(body.rule);
  throw failure(response, body, "设备报警操作结果缺失");
}
