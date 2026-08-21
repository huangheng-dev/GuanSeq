import { afterEach, describe, expect, it, vi } from "vitest";

import { loadWorkspaceSession, selectWorkspace } from "./workspace-client-service";

const session = {
  userId: "20000000-0000-4000-8000-000000000001",
  username: "lin.hao",
  displayName: "林浩",
  currentWorkspaceId: "10000000-0000-4000-8000-000000000101",
  selectionVersion: 0,
  workspaces: [{
    id: "10000000-0000-4000-8000-000000000101",
    code: "EAST-MFG",
    name: "华东制造中心",
    organizationId: "00000000-0000-4000-8000-000000000101",
    companyName: "示例精工制造有限公司",
    roleCode: "PLANNING_MANAGER",
    current: true,
  }],
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("workspace client service", () => {
  it("parses the workspace session contract", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ source: "backend", session }))));

    await expect(loadWorkspaceSession()).resolves.toEqual({ source: "backend", session });
  });

  it("sends workspace id and optimistic version when switching", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      source: "backend",
      session: { ...session, selectionVersion: 1 },
    })));
    vi.stubGlobal("fetch", fetchMock);

    await selectWorkspace(session.currentWorkspaceId, 0);

    expect(fetchMock).toHaveBeenCalledWith("/api/workspaces", expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({ workspaceId: session.currentWorkspaceId, expectedVersion: 0 }),
    }));
  });
});
