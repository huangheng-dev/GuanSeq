import "server-only";

import { randomUUID } from "node:crypto";
import { nonconformancePageSchema, nonconformanceSchema, type Nonconformance, type NonconformanceAction,
  type NonconformanceFilters, type NonconformancePage, type NonconformanceView } from "@/lib/nonconformance-contracts";
import { readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

const VIEWS: Record<string, NonconformanceView> = {
  "/quality/nonconformance/records": "records", "/quality/nonconformance/reviews": "reviews", "/quality/nonconformance/actions": "actions",
};
const QUEUES: Record<NonconformanceView, string> = { records: "ALL", reviews: "REVIEW", actions: "ACTION" };

export type NonconformancePageData =
  | { source: "backend"; page: NonconformancePage; view: NonconformanceView; requestId: string }
  | { source: "unavailable"; page: null; view: NonconformanceView; status: number; message: string; requestId: string };

function apiPath(filters: NonconformanceFilters) {
  const parameters = new URLSearchParams({ page: String(filters.page ?? 0), size: String(filters.size ?? 20), queue: filters.queue ?? "ALL",
    status: filters.status ?? "ALL", severity: filters.severity ?? "ALL", sourceType: filters.sourceType ?? "ALL", overdue: String(filters.overdue ?? false) });
  if (filters.query) parameters.set("query", filters.query);
  return `/api/v1/quality/nonconformances?${parameters.toString()}`;
}
async function message(response: Response, fallback: string) { try { return ((await response.json()) as { message?: string }).message ?? fallback; } catch { return fallback; } }

export async function loadNonconformancePage(requestId: string, view: NonconformanceView, filters: NonconformanceFilters = {}): Promise<NonconformancePageData> {
  const response = await requestGuanSeqApi(apiPath({ ...filters, queue: filters.queue ?? QUEUES[view] }), requestId, undefined, 10000);
  if (!response) return { source: "unavailable", page: null, view, status: 503, message: "质量不合格服务暂时不可用", requestId };
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  if (!response.ok) return { source: "unavailable", page: null, view, status: response.status,
    message: await message(response, response.status === 403 ? "当前角色无权查看不合格与 CAPA" : "不合格记录加载失败"), requestId: responseRequestId };
  return { source: "backend", page: nonconformancePageSchema.parse(await response.json()), view, requestId: responseRequestId };
}

export async function getNonconformance(id: string, requestId: string): Promise<Nonconformance> {
  const response = await requestGuanSeqApi(`/api/v1/quality/nonconformances/${id}`, requestId, undefined, 10000);
  if (!response) throw new Error("质量不合格服务当前无响应");
  if (!response.ok) await readApiError(response, "不合格详情加载失败");
  return nonconformanceSchema.parse(await response.json());
}

export async function mutateNonconformance(id: string, input: NonconformanceAction, requestId: string): Promise<Nonconformance> {
  const response = await requestGuanSeqApi(`/api/v1/quality/nonconformances/${id}/actions`, requestId,
    { method: "POST", body: JSON.stringify(input) }, 10000);
  if (!response) throw new Error("质量不合格服务当前无响应，未形成业务事实");
  if (!response.ok) await readApiError(response, "不合格处理失败");
  return nonconformanceSchema.parse(await response.json());
}

export async function getNonconformancePageData(pathname: string) {
  const view = VIEWS[pathname];
  return view ? loadNonconformancePage(`web-quality-nc-${randomUUID()}`, view) : null;
}
