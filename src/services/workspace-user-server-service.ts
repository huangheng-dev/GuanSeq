import "server-only";

import { randomUUID } from "node:crypto";

import {
  workspaceUserPageSchema,
  workspaceUserSchema,
  type WorkspaceRoleCode,
  type WorkspaceUserPage,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type WorkspaceUserPageData =
  | { source: "backend"; page: WorkspaceUserPage; requestId: string }
  | { source: "unavailable"; page: null; status: number; message: string; requestId: string };

async function responseMessage(response: Response, fallback: string) {
  try {
    const body = await response.json() as { message?: string };
    return body.message ?? fallback;
  } catch {
    return fallback;
  }
}

export async function loadWorkspaceUserPage(requestId: string): Promise<WorkspaceUserPageData> {
  const response = await requestGuanSeqApi(
    "/api/v1/identity/workspace-users?page=0&size=100&status=ALL",
    requestId,
  );
  if (!response) {
    return { source: "unavailable", page: null, status: 503, message: "成员管理服务暂时不可用", requestId };
  }
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  if (!response.ok) {
    return {
      source: "unavailable",
      page: null,
      status: response.status,
      message: await responseMessage(response, response.status === 403 ? "当前角色无权管理工作区成员" : "成员管理服务加载失败"),
      requestId: responseRequestId,
    };
  }
  return { source: "backend", page: workspaceUserPageSchema.parse(await response.json()), requestId: responseRequestId };
}

export async function getWorkspaceUserPageData(pathname: string): Promise<WorkspaceUserPageData | null> {
  if (pathname !== "/settings/organization/users") return null;
  return loadWorkspaceUserPage(`web-workspace-users-${randomUUID()}`);
}

type WorkspaceUserMutation =
  | { action: "create"; username: string; displayName: string; roleCode: WorkspaceRoleCode }
  | { action: "update"; userId: string; displayName: string; roleCode: WorkspaceRoleCode; expectedUserVersion: number; expectedMembershipVersion: number }
  | { action: "changeStatus"; userId: string; nextStatus: "ACTIVE" | "INACTIVE"; expectedMembershipVersion: number; reason: string };

export async function mutateWorkspaceUser(input: WorkspaceUserMutation, requestId: string) {
  const create = input.action === "create";
  const update = input.action === "update";
  const path = create
    ? "/api/v1/identity/workspace-users"
    : update
      ? `/api/v1/identity/workspace-users/${input.userId}`
      : `/api/v1/identity/workspace-users/${input.userId}/actions`;
  const method = update ? "PUT" : "POST";
  const body = create
    ? { username: input.username, displayName: input.displayName, roleCode: input.roleCode }
    : update
      ? {
          displayName: input.displayName,
          roleCode: input.roleCode,
          expectedUserVersion: input.expectedUserVersion,
          expectedMembershipVersion: input.expectedMembershipVersion,
        }
      : {
          action: input.nextStatus === "ACTIVE" ? "ACTIVATE" : "DEACTIVATE",
          expectedMembershipVersion: input.expectedMembershipVersion,
          reason: input.reason,
        };
  const response = await requestGuanSeqApi(path, requestId, { method, body: JSON.stringify(body) });
  if (!response) throw new GuanSeqApiError("成员管理服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "成员操作失败");
  return {
    user: workspaceUserSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}
