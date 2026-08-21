import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { actOnOperationTask } from "@/services/operation-task-server-service";

const mutation = z.object({
  id: z.string().uuid(),
  action: z.enum(["START", "COMPLETE"]),
  expectedVersion: z.number().int().nonnegative(),
  shiftName: z.string().max(80).nullable().optional(),
  operatorName: z.string().max(80).nullable().optional(),
  completedQuantity: z.number().positive().nullable().optional(),
  note: z.string().max(500).nullable().optional(),
});

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json({ message: "车间工序操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  }
  try {
    const result = await actOnOperationTask(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      { message: error instanceof Error ? error.message : "车间工序执行服务发生未预期错误", requestId },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
