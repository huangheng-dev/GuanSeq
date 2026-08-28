import { loadRolePermissionPage } from "@/services/role-permission-server-service";

export async function GET(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const result = await loadRolePermissionPage(requestId);
  if (result.source === "unavailable") {
    return Response.json(
      { message: result.message, requestId: result.requestId },
      { status: result.status, headers: { "X-Request-Id": result.requestId } },
    );
  }
  return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } });
}
