import { z } from "zod";

import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutateSalesOrder } from "@/services/sales-order-server-service";

const lineSchema = z.object({ materialId: z.string().uuid(), quantity: z.number().positive(), unitPrice: z.number().nonnegative() });
const writePayload = z.object({
  customerId: z.string().uuid(),
  currency: z.enum(["CNY", "USD", "EUR"]),
  taxRate: z.number().min(0).max(1),
  requestedDeliveryDate: z.string().min(1),
  promisedDeliveryDate: z.string().nullable(),
  owner: z.string().min(1),
  lines: z.array(lineSchema).min(1).max(100),
});
const mutationSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("create"), payload: writePayload }),
  z.object({ operation: z.literal("update"), id: z.string().uuid(), payload: writePayload.extend({ expectedVersion: z.number().int().nonnegative() }) }),
  z.object({ operation: z.literal("action"), id: z.string().uuid(), action: z.enum(["SUBMIT", "APPROVE", "REJECT", "RELEASE"]), expectedVersion: z.number().int().nonnegative(), comment: z.string().max(500).optional() }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "销售订单操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateSalesOrder(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    const message = error instanceof Error ? error.message : "销售订单服务发生未预期错误";
    return Response.json({ message, requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
