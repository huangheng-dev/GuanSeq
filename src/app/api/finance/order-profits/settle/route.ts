import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { settleOrderProfit } from "@/services/order-profit-server-service";

const schema = z.object({ salesOrderId: z.string().uuid() });

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = schema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "订单利润结算参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await settleOrderProfit(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "订单利润服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
