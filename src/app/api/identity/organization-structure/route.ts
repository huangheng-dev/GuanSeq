import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { loadOrganizationStructurePage, mutateOrganization } from "@/services/organization-structure-server-service";

const nullableUser = z.string().uuid().nullable();
const mutationSchema = z.discriminatedUnion("action", [
  z.object({ action: z.literal("createSite"), code: z.string().regex(/^[A-Z0-9][A-Z0-9_-]*$/).max(40), name: z.string().trim().min(1).max(120), responsibleUserId: nullableUser }),
  z.object({ action: z.literal("updateUnit"), unitId: z.string().uuid(), name: z.string().trim().min(1).max(120), responsibleUserId: nullableUser, expectedVersion: z.number().int().nonnegative() }),
  z.object({ action: z.literal("changeUnitStatus"), unitId: z.string().uuid(), nextStatus: z.enum(["ACTIVE", "INACTIVE"]), expectedVersion: z.number().int().nonnegative(), reason: z.string().trim().min(4).max(300) }),
  z.object({ action: z.literal("updateWorkspace"), name: z.string().trim().min(1).max(120), responsibleUserId: nullableUser, expectedVersion: z.number().int().nonnegative() }),
  z.object({ action: z.literal("assignMember"), userId: z.string().uuid(), organizationUnitId: z.string().uuid(), expectedMembershipVersion: z.number().int().nonnegative(), reason: z.string().trim().min(4).max(300) }),
]);
const requestIdFrom = (request: Request) => request.headers.get("X-Request-Id") ?? crypto.randomUUID();
export async function GET(request: Request) {
  const requestId = requestIdFrom(request); const result = await loadOrganizationStructurePage(requestId);
  if (result.source === "unavailable") return Response.json({ message: result.message, requestId: result.requestId }, { status: result.status, headers: { "X-Request-Id": result.requestId } });
  return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } });
}
export async function POST(request: Request) {
  const requestId = requestIdFrom(request); const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "组织操作参数无效，请检查编码、必填项、版本和原因", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try { const result = await mutateOrganization(parsed.data, requestId); return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } }); }
  catch (error) { const status = error instanceof GuanSeqApiError ? error.status : 500; return Response.json({ message: error instanceof Error ? error.message : "组织服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } }); }
}
