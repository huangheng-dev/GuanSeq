import { afterEach, describe, expect, it, vi } from "vitest";

import type { WorkspaceUser } from "@/lib/contracts";
import {
  changeWorkspaceUserStatus,
  createWorkspaceUser,
  refreshWorkspaceUsers,
  updateWorkspaceUser,
  WorkspaceUserClientError,
} from "./workspace-user-client-service";

const user: WorkspaceUser = {
  userId: "20000000-0000-4000-8000-000000000002",
  username: "pilot.operator",
  displayName: "试点操作员",
  accountStatus: "ACTIVE",
  membershipId: "30000000-0000-4000-8000-000000000002",
  membershipStatus: "ACTIVE",
  roleCode: "PRODUCTION_OPERATOR" as const,
  userVersion: 0,
  membershipVersion: 0,
  createdAt: "2026-08-24T08:00:00Z",
  updatedAt: "2026-08-24T08:00:00Z",
};

const page = {
  currentUserId: "20000000-0000-4000-8000-000000000001",
  workspaceId: "10000000-0000-4000-8000-000000000101",
  workspaceCode: "EAST-MFG",
  workspaceName: "华东制造中心",
  companyName: "示例精工制造有限公司",
  availableRoles: [{ code: "PRODUCTION_OPERATOR" as const, name: "生产操作员", description: "执行生产动作" }],
  items: [user],
  totalElements: 1,
  page: 0,
  size: 100,
  totalPages: 1,
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("workspace user client service", () => {
  it("parses the current-workspace member page", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ page })));

    await expect(refreshWorkspaceUsers()).resolves.toEqual(page);
  });

  it("sends controlled create, update and status mutations with optimistic versions", async () => {
    const fetchMock = vi.fn().mockImplementation(async () => Response.json({ user }));
    vi.stubGlobal("fetch", fetchMock);

    await createWorkspaceUser(user.username, user.displayName, user.roleCode);
    await updateWorkspaceUser(user, "试点生产操作员", "PRODUCTION_MANAGER");
    await changeWorkspaceUserStatus(user, "INACTIVE", "岗位调整暂停访问");

    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      action: "create",
      username: user.username,
      displayName: user.displayName,
      roleCode: user.roleCode,
    });
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(expect.objectContaining({
      action: "update",
      userId: user.userId,
      expectedUserVersion: 0,
      expectedMembershipVersion: 0,
    }));
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({
      action: "changeStatus",
      userId: user.userId,
      nextStatus: "INACTIVE",
      expectedMembershipVersion: 0,
      reason: "岗位调整暂停访问",
    });
  });

  it("keeps the backend request id in a failed mutation", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(
      { message: "成员关系已经变化", requestId: "req-conflict-1" },
      { status: 409 },
    )));

    await expect(updateWorkspaceUser(user, user.displayName, user.roleCode)).rejects.toEqual(
      expect.objectContaining<Partial<WorkspaceUserClientError>>({
        message: "成员关系已经变化",
        requestId: "req-conflict-1",
      }),
    );
  });
});
