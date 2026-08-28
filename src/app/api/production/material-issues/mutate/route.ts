import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { actOnMaterialIssue, createMaterialReturn } from "@/services/material-issue-server-service";

const issueLineSchema = z.object({
  lineId: z.string().uuid(), quantity: z.number().positive(), expectedLineVersion: z.number().int().nonnegative(),
  stockBalanceId: z.string().uuid().nullable().optional(), expectedStockVersion: z.number().int().nonnegative().nullable().optional(),
});
const returnLineSchema = z.object({ lineId: z.string().uuid(), quantity: z.number().positive(), expectedLineVersion: z.number().int().nonnegative(), reason: z.string().max(500).nullable().optional() });

const mutation = z.discriminatedUnion("operation", [
  z.object({
    operation: z.literal("action"),
    id: z.string().uuid(),
    action: z.enum(["ISSUE", "CANCEL"]),
    expectedVersion: z.number().int().nonnegative(),
    comment: z.string().max(500).nullable().optional(),
    source: z.enum(["DESKTOP_FORM", "MOBILE_SCAN"]).optional(),
    lines: z.array(issueLineSchema).max(200).optional(),
  }),
  z.object({
    operation: z.literal("return"),
    id: z.string().uuid(),
    locationId: z.string().uuid(),
    reason: z.string().trim().min(1).max(500),
    lines: z.array(returnLineSchema).min(1).max(200),
  }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "生产领退料操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = parsed.data.operation === "action"
      ? await actOnMaterialIssue(parsed.data, requestId)
      : await createMaterialReturn(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "生产备料服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
