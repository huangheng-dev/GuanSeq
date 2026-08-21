import type { IndependentDemandRecord } from "@/lib/contracts";
import type { PlanningDemandMutation } from "@/services/planning-demand-server-service";

export async function submitPlanningDemandMutation(input: PlanningDemandMutation): Promise<{ demand: IndependentDemandRecord; requestId: string }> {
  const response = await fetch("/api/planning/demands/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  const body = await response.json().catch(() => null) as { demand?: IndependentDemandRecord; requestId?: string; message?: string } | null;
  if (!response.ok || !body?.demand) throw new Error(body?.message ?? "计划需求操作失败，请稍后重试");
  return { demand: body.demand, requestId: body.requestId ?? "" };
}
