import { nonconformancePageSchema, nonconformanceSchema, type NonconformanceAction, type NonconformanceFilters,
  type NonconformanceView } from "@/lib/nonconformance-contracts";

export class NonconformanceClientError extends Error { constructor(message: string, readonly requestId?: string) { super(message); } }

async function payload(response: Response) { return await response.json().catch(() => null) as Record<string, unknown> | null; }
function failure(response: Response, body: Record<string, unknown> | null, fallback: string) {
  return new NonconformanceClientError(typeof body?.message === "string" ? body.message : fallback,
    typeof body?.requestId === "string" ? body.requestId : response.headers.get("X-Request-Id") ?? undefined);
}

export async function refreshNonconformances(view: NonconformanceView, filters: NonconformanceFilters = {}) {
  const parameters = new URLSearchParams({ view, page: String(filters.page ?? 0), size: String(filters.size ?? 20),
    status: filters.status ?? "ALL", severity: filters.severity ?? "ALL", sourceType: filters.sourceType ?? "ALL", overdue: String(filters.overdue ?? false) });
  if (filters.query) parameters.set("query", filters.query);
  const response = await fetch(`/api/quality/nonconformances?${parameters}`, { cache: "no-store" }); const body = await payload(response);
  if (!response.ok || !body) throw failure(response, body, "不合格记录刷新失败");
  return nonconformancePageSchema.parse(body.page);
}

export async function loadNonconformanceDetail(id: string) {
  const response = await fetch(`/api/quality/nonconformances?id=${encodeURIComponent(id)}`, { cache: "no-store" }); const body = await payload(response);
  if (!response.ok || !body) throw failure(response, body, "不合格详情加载失败");
  return nonconformanceSchema.parse(body.item);
}

export async function submitNonconformanceAction(id: string, action: NonconformanceAction, requestId: string) {
  const response = await fetch("/api/quality/nonconformances", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
    body: JSON.stringify({ id, ...action }) }); const body = await payload(response);
  if (!response.ok || !body) throw failure(response, body, "不合格处理失败；未形成业务事实");
  return nonconformanceSchema.parse(body);
}
