import { z } from "zod";

import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutateRouting } from "@/services/routing-server-service";

const operationSchema = z.object({
  operationCode: z.string().trim().min(1).max(40),
  operationName: z.string().trim().min(1).max(120),
  workCenterCode: z.string().trim().min(1).max(40),
  workCenterName: z.string().trim().min(1).max(120),
  setupMinutes: z.number().nonnegative(),
  runMinutesPerUnit: z.number().nonnegative(),
  queueMinutes: z.number().nonnegative(),
  inspectionRequired: z.boolean(),
  instructionSummary: z.string().max(500).nullable().optional(),
}).refine((value) => value.setupMinutes > 0 || value.runMinutesPerUnit > 0, "准备或单件工时至少一项必须大于 0");

const writePayload = z.object({
  materialId: z.string().uuid(), usageType: z.literal("PRODUCTION"), versionCode: z.string().trim().min(1).max(32),
  baseQuantity: z.number().positive(), effectiveFrom: z.string().min(1), owner: z.string().trim().min(1).max(80),
  changeReason: z.string().trim().min(1).max(500), operations: z.array(operationSchema).min(1).max(100),
});
const mutationSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("create"), payload: writePayload }),
  z.object({ operation: z.literal("update"), id: z.string().uuid(), payload: writePayload.extend({ expectedVersion: z.number().int().nonnegative() }) }),
  z.object({ operation: z.literal("action"), id: z.string().uuid(), action: z.enum(["PUBLISH", "INACTIVATE"]), expectedVersion: z.number().int().nonnegative() }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "工艺路线操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateRouting(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    const message = error instanceof Error ? error.message : "工艺路线服务发生未预期错误";
    return Response.json({ message, requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
