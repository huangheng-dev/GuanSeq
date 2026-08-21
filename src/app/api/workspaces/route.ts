import { z } from "zod";

import {
  getWorkspaceSession,
  switchWorkspace,
  WorkspaceServiceError,
} from "@/services/workspace-server-service";

const switchWorkspaceRequestSchema = z.object({
  workspaceId: z.string().uuid(),
  expectedVersion: z.number().int().nonnegative(),
});

function requestIdFrom(request: Request) {
  return request.headers.get("X-Request-Id") ?? crypto.randomUUID();
}

function errorResponse(error: unknown, requestId: string) {
  if (error instanceof WorkspaceServiceError) {
    return Response.json(
      { message: error.message, requestId },
      { status: error.status, headers: { "X-Request-Id": requestId } },
    );
  }
  return Response.json(
    { message: "工作区服务发生未预期错误", requestId },
    { status: 500, headers: { "X-Request-Id": requestId } },
  );
}

export async function GET(request: Request) {
  const requestId = requestIdFrom(request);
  try {
    const result = await getWorkspaceSession(requestId);
    return Response.json(
      { source: result.source, session: result.session },
      { headers: { "X-Request-Id": result.requestId } },
    );
  } catch (error) {
    return errorResponse(error, requestId);
  }
}

export async function PUT(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = switchWorkspaceRequestSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json(
      { message: "请选择有效工作区后重试", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }

  try {
    const result = await switchWorkspace(
      parsed.data.workspaceId,
      parsed.data.expectedVersion,
      requestId,
    );
    return Response.json(
      { source: result.source, session: result.session },
      { headers: { "X-Request-Id": result.requestId } },
    );
  } catch (error) {
    return errorResponse(error, requestId);
  }
}
