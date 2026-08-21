import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutateOperationLabor } from "@/services/operation-task-server-service";

const createMutation = z.object({
  kind: z.literal("CREATE"),
  taskId: z.string().uuid(),
  workDate: z.string().date(),
  shiftName: z.string().trim().min(1).max(80),
  operatorName: z.string().trim().min(1).max(80),
  actualMinutes: z.number().positive().max(1440),
  note: z.string().max(500).nullable().optional(),
});
const actionMutation = z.object({
  kind: z.literal("ACTION"),
  id: z.string().uuid(),
  action: z.enum(["APPROVE", "VOID"]),
  expectedVersion: z.number().int().nonnegative(),
  reason: z.string().max(500).nullable().optional(),
});
const mutation = z.discriminatedUnion("kind", [createMutation, actionMutation]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json({ message: "实际人工工时操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  }
  try {
    const result = await mutateOperationLabor(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      { message: error instanceof Error ? error.message : "实际人工工时服务发生未预期错误", requestId },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
