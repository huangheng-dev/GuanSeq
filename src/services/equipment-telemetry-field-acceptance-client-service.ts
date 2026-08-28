import { equipmentTelemetryFieldAcceptanceContextSchema,
  type EquipmentTelemetryFieldAcceptanceContext } from "@/lib/contracts";
import type { EquipmentTelemetryFieldAcceptanceMutation } from "@/services/equipment-telemetry-field-acceptance-server-service";

export class EquipmentTelemetryFieldAcceptanceClientError extends Error {
  constructor(message: string, readonly status: number, readonly requestId?: string) { super(message); }
}

async function payload(response: Response) {
  return await response.json().catch(() => null) as Record<string, unknown> | null;
}
function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new EquipmentTelemetryFieldAcceptanceClientError(
    typeof body?.message === "string" ? body.message : fallback, response.status,
    typeof body?.requestId === "string" ? body.requestId : response.headers.get("X-Request-Id") ?? undefined);
}

export async function loadEquipmentTelemetryFieldAcceptanceContext(connectionId: string): Promise<
  EquipmentTelemetryFieldAcceptanceContext
> {
  const response = await fetch(`/api/equipment/telemetry-field-acceptances?connectionId=${encodeURIComponent(connectionId)}`,
    { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.context) throw failure(response, body, "现场接入验收加载失败");
  return equipmentTelemetryFieldAcceptanceContextSchema.parse(body.context);
}

export async function submitEquipmentTelemetryFieldAcceptanceMutation(
  input: EquipmentTelemetryFieldAcceptanceMutation): Promise<EquipmentTelemetryFieldAcceptanceContext> {
  const response = await fetch("/api/equipment/telemetry-field-acceptances", { method: "POST",
    headers: { "Content-Type": "application/json",
      "X-Request-Id": `web-equipment-field-acceptance-${crypto.randomUUID()}` }, body: JSON.stringify(input) });
  const body = await payload(response);
  if (!response.ok || !body?.context) throw failure(response, body, "现场接入验收操作失败");
  return equipmentTelemetryFieldAcceptanceContextSchema.parse(body.context);
}
