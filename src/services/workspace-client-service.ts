import {
  workspaceSessionEnvelopeSchema,
  type WorkspaceSessionEnvelope,
} from "@/lib/contracts";

async function parseResponse(response: Response): Promise<WorkspaceSessionEnvelope> {
  if (!response.ok) {
    let message = "工作区操作失败，请稍后重试";
    try {
      const problem = await response.json() as { message?: string };
      message = problem.message ?? message;
    } catch {
      // 非 JSON 错误响应使用统一提示。
    }
    throw new Error(message);
  }
  return workspaceSessionEnvelopeSchema.parse(await response.json());
}

export async function loadWorkspaceSession(): Promise<WorkspaceSessionEnvelope> {
  return parseResponse(await fetch("/api/workspaces", { cache: "no-store" }));
}

export async function selectWorkspace(
  workspaceId: string,
  expectedVersion: number,
): Promise<WorkspaceSessionEnvelope> {
  return parseResponse(await fetch("/api/workspaces", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ workspaceId, expectedVersion }),
  }));
}
