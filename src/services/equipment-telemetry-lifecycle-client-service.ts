import { equipmentTelemetryAutomationActionResultSchema, equipmentTelemetryCleanupResultSchema,
  equipmentTelemetryRetentionPolicySchema, equipmentTelemetrySamplePageSchema,
  type EquipmentTelemetryAutomationActionResult, type EquipmentTelemetryCleanupResult,
  type EquipmentTelemetryQuality, type EquipmentTelemetryRetentionPolicy,
  type EquipmentTelemetrySamplePage } from "@/lib/contracts";
import type { EquipmentTelemetryLifecycleMutation } from "@/services/equipment-telemetry-server-service";
import { EquipmentTelemetryClientError } from "@/services/equipment-telemetry-client-service";

async function payload(response: Response) {
  return await response.json().catch(() => null) as Record<string, unknown> | null;
}

function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new EquipmentTelemetryClientError(typeof body?.message === "string" ? body.message : fallback,
    response.status, typeof body?.requestId === "string" ? body.requestId
      : response.headers.get("X-Request-Id") ?? undefined);
}

export async function loadEquipmentTelemetrySampleHistory(input: { connectionId: string;
  pointCode?: string; quality?: EquipmentTelemetryQuality }): Promise<EquipmentTelemetrySamplePage> {
  const params = new URLSearchParams({ resource: "history", connectionId: input.connectionId });
  if (input.pointCode) params.set("pointCode", input.pointCode);
  if (input.quality) params.set("quality", input.quality);
  const response = await fetch(`/api/equipment/telemetry-lifecycle?${params}`, { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.page) throw failure(response, body, "设备样本历史加载失败");
  return equipmentTelemetrySamplePageSchema.parse(body.page);
}

export async function loadEquipmentTelemetryRetention(): Promise<EquipmentTelemetryRetentionPolicy> {
  const response = await fetch("/api/equipment/telemetry-lifecycle?resource=policy", { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.policy) throw failure(response, body, "设备样本保留策略加载失败");
  return equipmentTelemetryRetentionPolicySchema.parse(body.policy);
}

export async function submitEquipmentTelemetryLifecycleMutation(input: EquipmentTelemetryLifecycleMutation): Promise<
  EquipmentTelemetryRetentionPolicy | EquipmentTelemetryCleanupResult | EquipmentTelemetryAutomationActionResult> {
  const response = await fetch("/api/equipment/telemetry-lifecycle", { method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-equipment-lifecycle-${crypto.randomUUID()}` },
    body: JSON.stringify(input) });
  const body = await payload(response);
  if (!response.ok) throw failure(response, body, "设备样本保留操作失败");
  if (body?.cleanupResult) return equipmentTelemetryCleanupResultSchema.parse(body.cleanupResult);
  if (body?.automationResult) return equipmentTelemetryAutomationActionResultSchema.parse(body.automationResult);
  if (!body?.policy) throw failure(response, body, "设备样本保留操作结果缺失");
  return equipmentTelemetryRetentionPolicySchema.parse(body.policy);
}
