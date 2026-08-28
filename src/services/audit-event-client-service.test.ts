import { afterEach, describe, expect, it, vi } from "vitest";

import { AuditEventClientError, refreshAuditEvents } from "./audit-event-client-service";

const page = {
  workspaceId: "10000000-0000-4000-8000-000000000101", workspaceCode: "EAST-MFG",
  workspaceName: "华东制造中心", companyName: "示例精工制造有限公司", scopeDescription: "当前工作区治理审计",
  occurredFrom: "2026-08-01T00:00:00Z", occurredTo: "2026-08-28T00:00:00Z",
  items: [{ id: "70000000-0000-4000-8000-000000000001", eventType: "WORKSPACE_SWITCHED", objectType: "WORKSPACE",
    objectId: "10000000-0000-4000-8000-000000000101", requestId: "audit-test-1",
    actorId: "20000000-0000-4000-8000-000000000001", actorUsername: "lin.hao", actorDisplayName: "林浩",
    details: { source: "test" }, occurredAt: "2026-08-28T00:00:00Z" }],
  totalElements: 1, page: 0, size: 20, totalPages: 1,
  eventTypes: ["WORKSPACE_SWITCHED"], objectTypes: ["WORKSPACE"],
  actors: [{ id: "20000000-0000-4000-8000-000000000001", username: "lin.hao", displayName: "林浩" }],
};

afterEach(() => vi.unstubAllGlobals());

describe("audit event client service", () => {
  it("forwards filters and parses the current workspace audit page", async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ page }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(refreshAuditEvents({ eventType: "WORKSPACE_SWITCHED", query: "audit-test", page: 1 })).resolves.toEqual(page);
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("eventType=WORKSPACE_SWITCHED"), { cache: "no-store" });
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("query=audit-test"), { cache: "no-store" });
  });

  it("keeps the backend request id on forbidden response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(
      { message: "只有管理员可以查看", requestId: "audit-forbidden-1" }, { status: 403 },
    )));
    await expect(refreshAuditEvents()).rejects.toEqual(expect.objectContaining<Partial<AuditEventClientError>>({
      message: "只有管理员可以查看", requestId: "audit-forbidden-1",
    }));
  });
});
