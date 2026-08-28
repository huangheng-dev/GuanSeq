import "server-only";

import { randomUUID } from "node:crypto";

import { workspaceAuditPageSchema, type WorkspaceAuditPage } from "@/lib/contracts";
import { requestGuanSeqApi } from "@/services/guanseq-api-server";

export type AuditEventFilters = {
  page?: number;
  size?: number;
  eventType?: string;
  objectType?: string;
  actorId?: string;
  query?: string;
  occurredFrom?: string;
  occurredTo?: string;
};

export type AuditEventPageData =
  | { source: "backend"; page: WorkspaceAuditPage; requestId: string }
  | { source: "unavailable"; page: null; status: number; message: string; requestId: string };

function apiPath(filters: AuditEventFilters) {
  const parameters = new URLSearchParams();
  parameters.set("page", String(filters.page ?? 0));
  parameters.set("size", String(filters.size ?? 20));
  for (const key of ["eventType", "objectType", "actorId", "query", "occurredFrom", "occurredTo"] as const) {
    const value = filters[key];
    if (value) parameters.set(key, value);
  }
  return `/api/v1/identity/audit-events?${parameters.toString()}`;
}

async function responseMessage(response: Response, fallback: string) {
  try { return ((await response.json()) as { message?: string }).message ?? fallback; } catch { return fallback; }
}

export async function loadAuditEventPage(requestId: string, filters: AuditEventFilters = {}): Promise<AuditEventPageData> {
  const response = await requestGuanSeqApi(apiPath(filters), requestId);
  if (!response) return { source: "unavailable", page: null, status: 503, message: "系统操作审计暂时不可用", requestId };
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  if (!response.ok) return {
    source: "unavailable", page: null, status: response.status,
    message: await responseMessage(response, response.status === 403 ? "当前角色无权查看系统操作审计" : "系统操作审计加载失败"),
    requestId: responseRequestId,
  };
  return { source: "backend", page: workspaceAuditPageSchema.parse(await response.json()), requestId: responseRequestId };
}

export async function getAuditEventPageData(pathname: string): Promise<AuditEventPageData | null> {
  if (pathname !== "/settings/audit") return null;
  return loadAuditEventPage(`web-audit-events-${randomUUID()}`);
}
