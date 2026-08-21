import type { MaterialPlanningParameter } from "@/lib/contracts";
export async function savePlanningParameter(materialId: string, leadTimeDays: number, expectedVersion: number): Promise<MaterialPlanningParameter> {
  const response = await fetch("/api/planning/material-parameters/mutate", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": `web-plan-param-${crypto.randomUUID()}` }, body: JSON.stringify({ materialId, leadTimeDays, expectedVersion }) });
  const payload = await response.json().catch(() => null) as { parameter?: MaterialPlanningParameter; message?: string } | null;
  if (!response.ok || !payload?.parameter) throw new Error(payload?.message ?? "计划参数保存失败");
  return payload.parameter;
}
