import { z } from "zod";

import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutateBom } from "@/services/bom-server-service";

const lineSchema = z.object({
  componentMaterialId: z.string().uuid(),
  quantity: z.number().positive(),
  scrapRate: z.number().min(0).lt(1),
  note: z.string().max(240).nullable().optional(),
});
const writePayload = z.object({
  parentMaterialId: z.string().uuid(),
  usageType: z.literal("PRODUCTION"),
  versionCode: z.string().trim().min(1).max(32),
  baseQuantity: z.number().positive(),
  effectiveFrom: z.string().min(1),
  owner: z.string().trim().min(1).max(80),
  changeReason: z.string().trim().min(1).max(500),
  lines: z.array(lineSchema).min(1).max(500),
});
const mutationSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("create"), payload: writePayload }),
  z.object({ operation: z.literal("update"), id: z.string().uuid(), payload: writePayload.extend({ expectedVersion: z.number().int().nonnegative() }) }),
  z.object({ operation: z.literal("action"), id: z.string().uuid(), action: z.enum(["PUBLISH", "INACTIVATE"]), expectedVersion: z.number().int().nonnegative() }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "BOM 操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateBom(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    const message = error instanceof Error ? error.message : "BOM 服务发生未预期错误";
    return Response.json({ message, requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
