import type { OrderProfitRecord } from "@/lib/contracts";
import type { SettleOrderProfitPayload } from "@/services/order-profit-server-service";

export async function submitSettleOrderProfit(payload: SettleOrderProfitPayload): Promise<OrderProfitRecord> {
  const response = await fetch("/api/finance/order-profits/settle", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-order-profit-${crypto.randomUUID()}` },
    body: JSON.stringify(payload),
  });
  const data = await response.json().catch(() => null) as { settlement?: OrderProfitRecord; message?: string } | null;
  if (!response.ok || !data?.settlement) throw new Error(data?.message ?? "订单利润结算失败，请重试");
  return data.settlement;
}
