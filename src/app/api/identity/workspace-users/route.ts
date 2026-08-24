import { z } from "zod";

import { workspaceRoleCodeSchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { loadWorkspaceUserPage, mutateWorkspaceUser } from "@/services/workspace-user-server-service";

const mutationSchema = z.discriminatedUnion("action", [
  z.object({ action: z.literal("create"), username: z.string().min(1).max(80).regex(/^\S+$/), displayName: z.string().trim().min(1).max(80), roleCode: workspaceRoleCodeSchema }),
  z.object({ action: z.literal("update"), userId: z.string().uuid(), displayName: z.string().trim().min(1).max(80), roleCode: workspaceRoleCodeSchema, expectedUserVersion: z.number().int().nonnegative(), expectedMembershipVersion: z.number().int().nonnegative() }),
  z.object({ action: z.literal("changeStatus"), userId: z.string().uuid(), nextStatus: z.enum(["ACTIVE", "INACTIVE"]), expectedMembershipVersion: z.number().int().nonnegative(), reason: z.string().trim().min(4).max(300) }),
]);

function requestIdFrom(request: Request) {
  return request.headers.get("X-Request-Id") ?? crypto.randomUUID();
}

export async function GET(request: Request) {
  const requestId = requestIdFrom(request);
  const result = await loadWorkspaceUserPage(requestId);
  if (result.source === "unavailable") {
    return Response.json(
      { message: result.message, requestId: result.requestId },
      { status: result.status, headers: { "X-Request-Id": result.requestId } },
    );
  }
  return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } });
}

export async function POST(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json(
      { message: "成员操作参数无效，请检查必填项、角色和原因", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } },
    );
  }
  try {
    const result = await mutateWorkspaceUser(parsed.data, requestId);
    return Response.json({ user: result.user }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      { message: error instanceof Error ? error.message : "成员服务发生未预期错误", requestId },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
