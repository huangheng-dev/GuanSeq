import "server-only";

import { randomUUID } from "node:crypto";

import {
  independentDemandPageSchema,
  independentDemandRecordSchema,
  planningDemandReferenceDataSchema,
  type IndependentDemandRecord,
  type PlanningDemandReferenceData,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type PlanningDemandPageData = {
  source: "backend";
  demands: IndependentDemandRecord[];
  references: PlanningDemandReferenceData;
};

export async function getPlanningDemandPageData(pathname: string): Promise<PlanningDemandPageData | null> {
  if (pathname !== "/planning/demand/independent") return null;
  const requestId = `web-planning-demand-${randomUUID()}`;
  const [demandsResponse, referencesResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/planning/independent-demands?page=0&size=100&status=ALL&sourceType=ALL", requestId),
    requestGuanSeqApi("/api/v1/planning/demand-reference-data", requestId),
  ]);
  if (!demandsResponse) return null;
  if (demandsResponse && !demandsResponse.ok) await readApiError(demandsResponse, "独立需求台账加载失败");
  if (!referencesResponse) return null;
  if (referencesResponse && !referencesResponse.ok) await readApiError(referencesResponse, "计划参考数据加载失败");
  return {
    source: "backend",
    demands: independentDemandPageSchema.parse(await demandsResponse.json()).items,
    references: planningDemandReferenceDataSchema.parse(await referencesResponse.json()),
  };
}

export type PlanningDemandWritePayload = {
  materialId: string;
  quantity: number;
  requiredDate: string;
  priority: "LOW" | "NORMAL" | "HIGH" | "URGENT";
  owner: string;
  note: string | null;
};

export type PlanningDemandMutation =
  | { operation: "create"; payload: PlanningDemandWritePayload }
  | { operation: "update"; id: string; payload: PlanningDemandWritePayload & { expectedVersion: number } }
  | { operation: "action"; id: string; action: "ACTIVATE" | "CANCEL"; expectedVersion: number; comment?: string };

export async function mutatePlanningDemand(input: PlanningDemandMutation, requestId: string) {
  const path = input.operation === "create"
    ? "/api/v1/planning/independent-demands"
    : input.operation === "update"
      ? `/api/v1/planning/independent-demands/${input.id}`
      : `/api/v1/planning/independent-demands/${input.id}/actions`;
  const method = input.operation === "update" ? "PUT" : "POST";
  const body = input.operation === "action"
    ? { action: input.action, expectedVersion: input.expectedVersion, comment: input.comment || null }
    : input.payload;
  const response = await requestGuanSeqApi(path, requestId, { method, body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("计划需求服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "计划需求服务暂时无法完成请求");
  return {
    demand: independentDemandRecordSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}
