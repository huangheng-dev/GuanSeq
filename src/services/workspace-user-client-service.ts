import type { WorkspaceRoleCode, WorkspaceUser, WorkspaceUserPage } from "@/lib/contracts";
import { workspaceUserPageSchema, workspaceUserSchema } from "@/lib/contracts";

export class WorkspaceUserClientError extends Error {
  constructor(message: string, readonly requestId?: string) {
    super(message);
  }
}

async function parse<T>(response: Response, select: (payload: Record<string, unknown>) => unknown, schema: { parse(value: unknown): T }): Promise<T> {
  const payload = await response.json().catch(() => null) as Record<string, unknown> | null;
  if (!response.ok || !payload) {
    throw new WorkspaceUserClientError(
      typeof payload?.message === "string" ? payload.message : "成员操作失败",
      typeof payload?.requestId === "string" ? payload.requestId : response.headers.get("X-Request-Id") ?? undefined,
    );
  }
  return schema.parse(select(payload));
}

export async function refreshWorkspaceUsers(): Promise<WorkspaceUserPage> {
  return parse(await fetch("/api/identity/workspace-users", { cache: "no-store" }), (payload) => payload.page, workspaceUserPageSchema);
}

export async function createWorkspaceUser(username: string, displayName: string, roleCode: WorkspaceRoleCode): Promise<WorkspaceUser> {
  return mutate({ action: "create", username, displayName, roleCode });
}

export async function updateWorkspaceUser(user: WorkspaceUser, displayName: string, roleCode: WorkspaceRoleCode): Promise<WorkspaceUser> {
  return mutate({
    action: "update",
    userId: user.userId,
    displayName,
    roleCode,
    expectedUserVersion: user.userVersion,
    expectedMembershipVersion: user.membershipVersion,
  });
}

export async function changeWorkspaceUserStatus(user: WorkspaceUser, nextStatus: "ACTIVE" | "INACTIVE", reason: string): Promise<WorkspaceUser> {
  return mutate({ action: "changeStatus", userId: user.userId, nextStatus, expectedMembershipVersion: user.membershipVersion, reason });
}

async function mutate(input: Record<string, unknown>): Promise<WorkspaceUser> {
  return parse(await fetch("/api/identity/workspace-users", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-identity-${crypto.randomUUID()}` },
    body: JSON.stringify(input),
  }), (payload) => payload.user, workspaceUserSchema);
}
