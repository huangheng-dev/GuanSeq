import { workspaceAuditPageSchema, type WorkspaceAuditPage } from "@/lib/contracts";
import type { AuditEventFilters } from "@/services/audit-event-server-service";

export class AuditEventClientError extends Error {
  constructor(message: string, readonly requestId?: string) { super(message); }
}

export async function refreshAuditEvents(filters: AuditEventFilters = {}): Promise<WorkspaceAuditPage> {
  const parameters = new URLSearchParams();
  parameters.set("page", String(filters.page ?? 0));
  parameters.set("size", String(filters.size ?? 20));
  for (const key of ["eventType", "objectType", "actorId", "query", "occurredFrom", "occurredTo"] as const) {
    const value = filters[key];
    if (value) parameters.set(key, value);
  }
  const response = await fetch(`/api/identity/audit-events?${parameters.toString()}`, { cache: "no-store" });
  const payload = await response.json().catch(() => null) as Record<string, unknown> | null;
  if (!response.ok || !payload) throw new AuditEventClientError(
    typeof payload?.message === "string" ? payload.message : "系统操作审计加载失败",
    typeof payload?.requestId === "string" ? payload.requestId : response.headers.get("X-Request-Id") ?? undefined,
  );
  return workspaceAuditPageSchema.parse(payload.page);
}
