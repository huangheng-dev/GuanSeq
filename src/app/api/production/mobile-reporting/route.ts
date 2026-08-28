import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { actOnOperationTask } from "@/services/operation-task-server-service";
import { mutateProductionExecution } from "@/services/production-execution-server-service";
import { fetchMobileProductionReportingPageData } from "@/services/mobile-production-reporting-server-service";

const mutation = z.discriminatedUnion("kind", [
  z.object({ kind: z.literal("TASK_ACTION"), id: z.string().uuid(), action: z.enum(["START", "COMPLETE"]),
    expectedVersion: z.number().int().nonnegative(), shiftName: z.string().min(1).max(80), completedQuantity: z.number().positive().nullable(),
    note: z.string().max(500).nullable(), operatorBadge: z.string().min(1).max(120) }),
  z.object({ kind: z.literal("WORK_REPORT"), orderId: z.string().uuid(), operationTaskId: z.string().uuid(), quantity: z.number().positive(),
    shiftName: z.string().min(1).max(80), note: z.string().max(500).nullable(), expectedOrderVersion: z.number().int().nonnegative(),
    operatorBadge: z.string().min(1).max(120) }),
]);

export async function GET() {
  const data = await fetchMobileProductionReportingPageData();
  return Response.json(data, { status: data.source === "backend" ? 200 : 503 });
}

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "生产扫码报工参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    if (parsed.data.kind === "TASK_ACTION") {
      const result = await actOnOperationTask({ id: parsed.data.id, action: parsed.data.action, expectedVersion: parsed.data.expectedVersion,
        shiftName: parsed.data.shiftName, operatorName: null, completedQuantity: parsed.data.completedQuantity, note: parsed.data.note,
        source: "MOBILE_SCAN", operatorBadge: parsed.data.operatorBadge }, requestId);
      return Response.json({ kind: "TASK_ACTION", ...result }, { headers: { "X-Request-Id": result.requestId } });
    }
    const result = await mutateProductionExecution({ operation: "report", orderId: parsed.data.orderId, quantity: parsed.data.quantity,
      shiftName: parsed.data.shiftName, operatorName: null, note: parsed.data.note, expectedOrderVersion: parsed.data.expectedOrderVersion,
      source: "MOBILE_SCAN", operationTaskId: parsed.data.operationTaskId, operatorBadge: parsed.data.operatorBadge }, requestId);
    return Response.json({ kind: "WORK_REPORT", ...result }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "生产扫码报工服务发生未预期错误", requestId },
      { status, headers: { "X-Request-Id": requestId } });
  }
}
