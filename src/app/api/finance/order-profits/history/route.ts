import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { getOrderProfitHistory } from "@/services/order-profit-server-service";

const schema = z.object({ salesOrderId: z.string().uuid() });

export async function GET(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const url = new URL(request.url);
  const parsed = schema.safeParse({ salesOrderId: url.searchParams.get("salesOrderId") });
  if (!parsed.success) return Response.json({ message: "订单利润历史参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const history = await getOrderProfitHistory(parsed.data.salesOrderId, requestId);
    return Response.json({ history, requestId }, { headers: { "X-Request-Id": requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "订单利润历史加载失败", requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
