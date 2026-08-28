import { equipmentMaintenanceGenerationSchema, equipmentMaintenancePlanPageSchema, equipmentMaintenancePlanSchema,
  type EquipmentMaintenanceGeneration, type EquipmentMaintenancePlan, type EquipmentMaintenancePlanPage } from "@/lib/contracts";
import type { EquipmentMaintenancePlanMutation } from "@/services/equipment-maintenance-plan-server-service";

export class EquipmentMaintenancePlanClientError extends Error {
  constructor(message: string, readonly status: number, readonly requestId?: string) { super(message); }
}

async function payload(response: Response) { return await response.json().catch(() => null) as Record<string, unknown> | null; }
function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new EquipmentMaintenancePlanClientError(typeof body?.message === "string" ? body.message : fallback, response.status,
    typeof body?.requestId === "string" ? body.requestId : response.headers.get("X-Request-Id") ?? undefined);
}

export async function refreshEquipmentMaintenancePlans(): Promise<EquipmentMaintenancePlanPage> {
  const response = await fetch("/api/equipment/maintenance-plans", { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.page) throw failure(response, body, "周期维护计划刷新失败");
  return equipmentMaintenancePlanPageSchema.parse(body.page);
}

export async function loadEquipmentMaintenancePlanDetail(id: string): Promise<EquipmentMaintenancePlan> {
  const response = await fetch(`/api/equipment/maintenance-plans?id=${encodeURIComponent(id)}`, { cache: "no-store" });
  const body = await payload(response);
  if (!response.ok || !body?.plan) throw failure(response, body, "周期维护模板详情加载失败");
  return equipmentMaintenancePlanSchema.parse(body.plan);
}

export async function submitEquipmentMaintenancePlanMutation(input: EquipmentMaintenancePlanMutation): Promise<
  EquipmentMaintenancePlan | EquipmentMaintenanceGeneration
> {
  const response = await fetch("/api/equipment/maintenance-plans", { method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-equipment-plan-${crypto.randomUUID()}` },
    body: JSON.stringify(input) });
  const body = await payload(response);
  if (!response.ok) throw failure(response, body, "周期维护计划操作失败");
  if (input.operation === "generateDue") {
    if (!body?.generation) throw failure(response, body, "到期任务生成结果缺失");
    return equipmentMaintenanceGenerationSchema.parse(body.generation);
  }
  if (!body?.plan) throw failure(response, body, "周期维护模板结果缺失");
  return equipmentMaintenancePlanSchema.parse(body.plan);
}
