import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutateProductionOrder } from "@/services/production-order-server-service";

const write = z.object({ materialId: z.string().uuid(), plannedQuantity: z.number().positive(), plannedStartDate: z.string().min(1), plannedReceiptDate: z.string().min(1), workshop: z.string().min(1).max(120), owner: z.string().min(1).max(80), sourceType: z.enum(["MANUAL", "MRP", "SALES_ORDER"]), sourceId: z.string().uuid().nullable(), sourceNumber: z.string().max(60).nullable() });
const mutation = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("create"), payload: write }),
  z.object({ operation: z.literal("update"), id: z.string().uuid(), payload: write.extend({ expectedVersion: z.number().int().nonnegative() }) }),
  z.object({ operation: z.literal("action"), id: z.string().uuid(), action: z.enum(["RELEASE", "START", "CANCEL"]), expectedVersion: z.number().int().nonnegative(), comment: z.string().max(500).optional() }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "生产订单操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try { const result = await mutateProductionOrder(parsed.data, requestId); return Response.json(result, { headers: { "X-Request-Id": result.requestId } }); }
  catch (error) { const status = error instanceof GuanSeqApiError ? error.status : 500; return Response.json({ message: error instanceof Error ? error.message : "生产订单服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } }); }
}
