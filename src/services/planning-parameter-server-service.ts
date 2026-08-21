import "server-only";
import { randomUUID } from "node:crypto";
import { materialPlanningParameterPageSchema, materialPlanningParameterSchema, type MaterialPlanningParameter } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type PlanningParameterPageData = { source: "backend"; parameters: MaterialPlanningParameter[] };
export async function getPlanningParameterPageData(pathname: string): Promise<PlanningParameterPageData | null> {
  if (pathname !== "/planning/parameters") return null;
  const response = await requestGuanSeqApi("/api/v1/planning/material-parameters?page=0&size=100", `web-planning-parameters-${randomUUID()}`);
  if (!response?.ok) return null;
  return { source: "backend", parameters: materialPlanningParameterPageSchema.parse(await response.json()).items };
}
export async function updatePlanningParameter(materialId: string, leadTimeDays: number, expectedVersion: number, requestId: string) {
  const response = await requestGuanSeqApi(`/api/v1/planning/material-parameters/${materialId}`, requestId, { method: "PUT", body: JSON.stringify({ leadTimeDays, expectedVersion }) });
  if (!response) throw new GuanSeqApiError("计划参数服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "计划参数保存失败");
  return { parameter: materialPlanningParameterSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
