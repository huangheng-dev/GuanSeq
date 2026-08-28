import "server-only";

import { randomUUID } from "node:crypto";

import { workspaceRolePermissionPageSchema, type WorkspaceRolePermissionPage } from "@/lib/contracts";
import { requestGuanSeqApi } from "@/services/guanseq-api-server";

export type RolePermissionPageData =
  | { source: "backend"; page: WorkspaceRolePermissionPage; requestId: string }
  | { source: "unavailable"; page: null; status: number; message: string; requestId: string };

async function responseMessage(response: Response, fallback: string) {
  try {
    const body = await response.json() as { message?: string };
    return body.message ?? fallback;
  } catch {
    return fallback;
  }
}

export async function loadRolePermissionPage(requestId: string): Promise<RolePermissionPageData> {
  const response = await requestGuanSeqApi("/api/v1/identity/role-permissions", requestId);
  if (!response) {
    return { source: "unavailable", page: null, status: 503, message: "角色权限目录暂时不可用", requestId };
  }
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  if (!response.ok) {
    return {
      source: "unavailable",
      page: null,
      status: response.status,
      message: await responseMessage(response, response.status === 403 ? "当前角色无权查看角色权限矩阵" : "角色权限目录加载失败"),
      requestId: responseRequestId,
    };
  }
  return { source: "backend", page: workspaceRolePermissionPageSchema.parse(await response.json()), requestId: responseRequestId };
}

export async function getRolePermissionPageData(pathname: string): Promise<RolePermissionPageData | null> {
  if (pathname !== "/settings/roles") return null;
  return loadRolePermissionPage(`web-role-permissions-${randomUUID()}`);
}
