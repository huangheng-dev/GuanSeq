import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutatePurchaseOrder } from "@/services/procurement-server-service";

const line = z.object({ materialId: z.string().uuid(), orderedQuantity: z.number().positive(), unitPrice: z.number().nonnegative() });
const write = z.object({ supplierId: z.string().uuid(), currency: z.enum(["CNY", "USD", "EUR"]), taxRate: z.number().min(0).max(1), requestedReceiptDate: z.string().min(1), promisedReceiptDate: z.string().nullable(), buyer: z.string().min(1), lines: z.array(line).min(1).max(100) });
const mutation = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("create"), payload: write }),
  z.object({ operation: z.literal("update"), id: z.string().uuid(), payload: write.extend({ expectedVersion: z.number().int().nonnegative() }) }),
  z.object({ operation: z.literal("action"), id: z.string().uuid(), action: z.enum(["SUBMIT", "APPROVE", "REJECT", "RELEASE"]), expectedVersion: z.number().int().nonnegative(), comment: z.string().max(500).optional() }),
]);
export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "采购订单操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try { const result = await mutatePurchaseOrder(parsed.data, requestId); return Response.json(result, { headers: { "X-Request-Id": result.requestId } }); }
  catch (error) { const status = error instanceof GuanSeqApiError ? error.status : 500; return Response.json({ message: error instanceof Error ? error.message : "采购订单服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } }); }
}
