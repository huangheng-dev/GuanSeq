import { afterEach, describe, expect, it, vi } from "vitest";

import { refreshRolePermissions, RolePermissionClientError } from "./role-permission-client-service";

const page = {
  workspaceId: "10000000-0000-4000-8000-000000000101",
  workspaceCode: "EAST-MFG",
  workspaceName: "华东制造中心",
  companyName: "示例精工制造有限公司",
  catalogVersion: "2026-08-28.2",
  scopeDescription: "只展示后端显式角色门禁。",
  roles: [{ code: "ADMIN" as const, name: "系统管理员", description: "全部已接入业务动作" }],
  groups: [{
    moduleCode: "IDENTITY",
    moduleName: "身份与工作区",
    permissions: [{ code: "IDENTITY_ROLE_MATRIX_READ", name: "查看角色权限矩阵", description: "读取后端目录", risk: "SENSITIVE" as const, roleCodes: ["ADMIN" as const] }],
  }],
};

afterEach(() => vi.unstubAllGlobals());

describe("role permission client service", () => {
  it("parses the backend-enforced role permission catalog", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ page })));
    await expect(refreshRolePermissions()).resolves.toEqual(page);
  });

  it("keeps the backend request id when refresh is forbidden", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(
      { message: "只有管理员可以查看", requestId: "role-permission-forbidden-1" },
      { status: 403 },
    )));
    await expect(refreshRolePermissions()).rejects.toEqual(expect.objectContaining<Partial<RolePermissionClientError>>({
      message: "只有管理员可以查看",
      requestId: "role-permission-forbidden-1",
    }));
  });
});
