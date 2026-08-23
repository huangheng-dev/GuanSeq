import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { resettleOrderProfit } from "@/services/order-profit-server-service";

const schema = z.object({
  salesOrderId: z.string().uuid(),
  reason: z.string().min(4).max(500),
  settlementDate: z.string().nullable().optional(),
  expectedVersion: z.number().int().nonnegative().nullable().optional(),
});

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = schema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "订单利润重算参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await resettleOrderProfit(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "订单利润重算发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
