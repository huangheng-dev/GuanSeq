import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutateProductionExecution } from "@/services/production-execution-server-service";

const mutation = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("report"), orderId: z.string().uuid(), quantity: z.number().positive(), shiftName: z.string().min(1).max(80), operatorName: z.string().min(1).max(80).nullable(), note: z.string().max(500).nullable(), expectedOrderVersion: z.number().int().nonnegative(), source: z.enum(["DESKTOP_FORM", "MOBILE_SCAN"]).optional(), operationTaskId: z.string().uuid().nullable().optional(), operatorBadge: z.string().max(120).nullable().optional() }),
  z.object({ operation: z.literal("settle"), id: z.string().uuid(), warehouseId: z.string().uuid().nullable(), locationId: z.string().uuid().nullable(), lotNumber: z.string().max(80).nullable(), expectedVersion: z.number().int().nonnegative() }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "生产执行操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try { const result = await mutateProductionExecution(parsed.data, requestId); return Response.json(result, { headers: { "X-Request-Id": result.requestId } }); }
  catch (error) { const status = error instanceof GuanSeqApiError ? error.status : 500; return Response.json({ message: error instanceof Error ? error.message : "生产执行服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } }); }
}
