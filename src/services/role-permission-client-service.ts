import { workspaceRolePermissionPageSchema, type WorkspaceRolePermissionPage } from "@/lib/contracts";

export class RolePermissionClientError extends Error {
  constructor(message: string, readonly requestId?: string) {
    super(message);
  }
}

export async function refreshRolePermissions(): Promise<WorkspaceRolePermissionPage> {
  const response = await fetch("/api/identity/role-permissions", { cache: "no-store" });
  const payload = await response.json().catch(() => null) as Record<string, unknown> | null;
  if (!response.ok || !payload) {
    throw new RolePermissionClientError(
      typeof payload?.message === "string" ? payload.message : "角色权限目录加载失败",
      typeof payload?.requestId === "string" ? payload.requestId : response.headers.get("X-Request-Id") ?? undefined,
    );
  }
  return workspaceRolePermissionPageSchema.parse(payload.page);
}
