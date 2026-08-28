import { z } from "zod";

import { loadAuditEventPage } from "@/services/audit-event-server-service";

const querySchema = z.object({
  page: z.coerce.number().int().min(0).default(0),
  size: z.coerce.number().int().min(1).max(100).default(20),
  eventType: z.string().max(80).optional(),
  objectType: z.string().max(80).optional(),
  actorId: z.string().uuid().optional(),
  query: z.string().max(120).optional(),
  occurredFrom: z.string().datetime({ offset: true }).optional(),
  occurredTo: z.string().datetime({ offset: true }).optional(),
});

export async function GET(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = querySchema.safeParse(Object.fromEntries(new URL(request.url).searchParams));
  if (!parsed.success) return Response.json(
    { message: "审计查询参数无效，请检查分页、筛选和时间范围", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } },
  );
  const result = await loadAuditEventPage(requestId, parsed.data);
  if (result.source === "unavailable") return Response.json(
    { message: result.message, requestId: result.requestId },
    { status: result.status, headers: { "X-Request-Id": result.requestId } },
  );
  return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } });
}
