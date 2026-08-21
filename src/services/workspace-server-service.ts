import "server-only";

import {
  workspaceSessionSchema,
  type WorkspaceSession,
} from "@/lib/contracts";
import { requestGuanSeqApi } from "@/services/guanseq-api-server";

const fallbackSession: WorkspaceSession = {
  userId: "20000000-0000-4000-8000-000000000001",
  username: "lin.hao",
  displayName: "林浩",
  currentWorkspaceId: "10000000-0000-4000-8000-000000000101",
  selectionVersion: 0,
  workspaces: [
    { id: "10000000-0000-4000-8000-000000000101", code: "EAST-MFG", name: "华东制造中心", organizationId: "00000000-0000-4000-8000-000000000101", companyName: "示例精工制造有限公司", roleCode: "PLANNING_MANAGER", current: true },
    { id: "10000000-0000-4000-8000-000000000102", code: "SOUTH-MFG", name: "华南制造中心", organizationId: "00000000-0000-4000-8000-000000000102", companyName: "示例精工制造有限公司", roleCode: "PLANNING_MANAGER", current: false },
    { id: "10000000-0000-4000-8000-000000000103", code: "NORTH-ASM", name: "北方装配基地", organizationId: "00000000-0000-4000-8000-000000000103", companyName: "示例精工制造有限公司", roleCode: "PLANNING_MANAGER", current: false },
  ],
};

export class WorkspaceServiceError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

type WorkspaceServerResult = {
  source: "backend" | "mock";
  session: WorkspaceSession;
  requestId: string;
};

async function parseBackendSession(response: Response): Promise<WorkspaceSession> {
  if (!response.ok) {
    let message = "工作区服务暂时无法完成请求";
    try {
      const problem = await response.json() as { detail?: string; message?: string };
      message = problem.detail ?? problem.message ?? message;
    } catch {
      // 非 JSON 错误响应仍按状态码向上游传递。
    }
    throw new WorkspaceServiceError(message, response.status);
  }

  try {
    return workspaceSessionSchema.parse(await response.json());
  } catch {
    throw new WorkspaceServiceError("工作区接口返回的数据不符合契约", 502);
  }
}

export async function getWorkspaceSession(requestId: string): Promise<WorkspaceServerResult> {
  const response = await requestGuanSeqApi("/api/v1/me/workspaces", requestId);
  if (!response) {
    return { source: "mock", session: fallbackSession, requestId };
  }
  return {
    source: "backend",
    session: await parseBackendSession(response),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}

export async function switchWorkspace(
  workspaceId: string,
  expectedVersion: number,
  requestId: string,
): Promise<WorkspaceServerResult> {
  const response = await requestGuanSeqApi("/api/v1/me/current-workspace", requestId, {
    method: "PUT",
    body: JSON.stringify({ workspaceId, expectedVersion }),
  });
  if (!response) {
    const session = workspaceSessionSchema.parse({
      ...fallbackSession,
      currentWorkspaceId: workspaceId,
      selectionVersion: expectedVersion + 1,
      workspaces: fallbackSession.workspaces.map((workspace) => ({
        ...workspace,
        current: workspace.id === workspaceId,
      })),
    });
    return { source: "mock", session, requestId };
  }
  return {
    source: "backend",
    session: await parseBackendSession(response),
    requestId: response.headers.get("X-Request-Id") ?? requestId,
  };
}
