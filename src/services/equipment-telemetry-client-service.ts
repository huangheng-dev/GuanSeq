import { equipmentTelemetryActionResultSchema, equipmentTelemetryConnectionPageSchema,
  equipmentTelemetryConnectionSchema, type EquipmentTelemetryActionResult,
  type EquipmentTelemetryConnection, type EquipmentTelemetryConnectionPage } from "@/lib/contracts";
import type { EquipmentTelemetryMutation } from "@/services/equipment-telemetry-server-service";

export class EquipmentTelemetryClientError extends Error {
  constructor(message: string, readonly status: number, readonly requestId?: string) { super(message); }
}

async function payload(response: Response) { return await response.json().catch(() => null) as Record<string, unknown> | null; }
function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new EquipmentTelemetryClientError(typeof body?.message === "string" ? body.message : fallback, response.status,
    typeof body?.requestId === "string" ? body.requestId : response.headers.get("X-Request-Id") ?? undefined);
}

export async function refreshEquipmentTelemetryConnections(): Promise<EquipmentTelemetryConnectionPage> {
  const response = await fetch("/api/equipment/telemetry-connections", { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.page) throw failure(response, body, "设备采集连接刷新失败");
  return equipmentTelemetryConnectionPageSchema.parse(body.page);
}

export async function loadEquipmentTelemetryConnectionDetail(id: string): Promise<EquipmentTelemetryConnection> {
  const response = await fetch(`/api/equipment/telemetry-connections?id=${encodeURIComponent(id)}`, { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.connection) throw failure(response, body, "设备采集连接详情加载失败");
  return equipmentTelemetryConnectionSchema.parse(body.connection);
}

export async function submitEquipmentTelemetryMutation(input: EquipmentTelemetryMutation): Promise<
  EquipmentTelemetryConnection | EquipmentTelemetryActionResult
> {
  const response = await fetch("/api/equipment/telemetry-connections", { method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-equipment-telemetry-${crypto.randomUUID()}` },
    body: JSON.stringify(input) });
  const body = await payload(response);
  if (!response.ok) throw failure(response, body, "设备采集连接操作失败");
  if (body?.actionResult) return equipmentTelemetryActionResultSchema.parse(body.actionResult);
  if (!body?.connection) throw failure(response, body, "设备采集连接结果缺失");
  return equipmentTelemetryConnectionSchema.parse(body.connection);
}
