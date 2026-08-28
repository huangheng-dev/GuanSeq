import "server-only";

import { randomUUID } from "node:crypto";
import { putawayPageSchema, putawayReferenceDataSchema, putawayTaskSchema, type PutawayReferenceData, type PutawayTask } from "@/lib/putaway-contracts";
import { readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

const PATHS = new Set(["/warehouse/receiving", "/warehouse/barcodes/scanning"]);
export type PutawayPageData = { source: "backend" | "unavailable"; references: PutawayReferenceData; tasks: PutawayTask[]; error?: string };
const emptyReferences: PutawayReferenceData = { sourceBalances: [], targetLocations: [] };

export async function fetchPutawayPageData(): Promise<PutawayPageData> {
  const requestId = `web-putaway-${randomUUID()}`;
  const [referenceResponse, tasksResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/warehouse/putaway-reference-data", requestId, undefined, 10000),
    requestGuanSeqApi("/api/v1/warehouse/putaway-tasks?page=0&size=100&status=ALL", `${requestId}-tasks`, undefined, 10000),
  ]);
  if (!referenceResponse || !tasksResponse) return { source: "unavailable", references: emptyReferences, tasks: [], error: "仓储上架服务暂时不可用，请确认后端连接后重试。" };
  if (!referenceResponse.ok || !tasksResponse.ok) {
    const denied = [referenceResponse.status, tasksResponse.status].some((status) => status === 401 || status === 403);
    return { source: "unavailable", references: emptyReferences, tasks: [], error: denied ? "当前账号无权执行仓储上架。" : "上架数据加载失败，请稍后刷新。" };
  }
  return { source: "backend", references: putawayReferenceDataSchema.parse(await referenceResponse.json()), tasks: putawayPageSchema.parse(await tasksResponse.json()).items };
}

export async function mutatePutaway(input: Record<string, unknown>, requestId: string) {
  const action = String(input.action ?? ""); const id = typeof input.id === "string" ? input.id : "";
  const path = action === "CREATE" ? "/api/v1/warehouse/putaway-tasks" : `/api/v1/warehouse/putaway-tasks/${id}/${action.toLowerCase()}`;
  const { action: _action, id: _id, ...body } = input; void _action; void _id;
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) }, 10000);
  if (!response) throw new Error("仓储上架服务当前无响应，未形成业务事实");
  if (!response.ok) await readApiError(response, "仓储上架操作失败");
  return putawayTaskSchema.parse(await response.json());
}

export async function getPutawayPageData(pathname: string) { return PATHS.has(pathname) ? fetchPutawayPageData() : null; }

