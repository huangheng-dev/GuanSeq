import { z } from "zod";

import { nonconformanceActionSchema } from "@/lib/nonconformance-contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { getNonconformance, loadNonconformancePage, mutateNonconformance } from "@/services/nonconformance-server-service";

const querySchema = z.object({
  id: z.string().uuid().optional(), view: z.enum(["records", "reviews", "actions"]).default("records"),
  query: z.string().max(120).optional(), status: z.enum(["ALL", "OPEN", "REVIEWED", "ACTION_REQUIRED", "ACTION_IN_PROGRESS", "VERIFICATION_PENDING", "CLOSED"]).default("ALL"),
  severity: z.enum(["ALL", "LOW", "MEDIUM", "HIGH", "CRITICAL"]).default("ALL"),
  sourceType: z.enum(["ALL", "INCOMING_INSPECTION", "FINAL_INSPECTION"]).default("ALL"),
  overdue: z.enum(["true", "false"]).default("false").transform((value) => value === "true"),
  page: z.coerce.number().int().min(0).default(0), size: z.coerce.number().int().min(1).max(100).default(20),
});
const mutationSchema = z.object({ id: z.string().uuid() }).and(nonconformanceActionSchema);

export async function GET(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = querySchema.safeParse(Object.fromEntries(new URL(request.url).searchParams));
  if (!parsed.success) return Response.json({ message: "不合格查询参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    if (parsed.data.id) return Response.json({ item: await getNonconformance(parsed.data.id, requestId) }, { headers: { "X-Request-Id": requestId } });
    const result = await loadNonconformancePage(requestId, parsed.data.view, parsed.data);
    if (result.source === "unavailable") return Response.json({ message: result.message, requestId: result.requestId },
      { status: result.status, headers: { "X-Request-Id": result.requestId } });
    return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : error instanceof Error && error.message.includes("无响应") ? 503 : 500;
    return Response.json({ message: error instanceof Error ? error.message : "质量不合格服务发生未预期错误", requestId },
      { status, headers: { "X-Request-Id": requestId } });
  }
}

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "不合格处理参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const { id, ...action } = parsed.data;
    return Response.json(await mutateNonconformance(id, action, requestId), { headers: { "X-Request-Id": requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : error instanceof Error && error.message.includes("无响应") ? 503 : 500;
    return Response.json({ message: error instanceof Error ? error.message : "质量不合格服务发生未预期错误", requestId },
      { status, headers: { "X-Request-Id": requestId } });
  }
}
