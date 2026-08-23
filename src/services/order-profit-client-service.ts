import type { OrderProfitRecord } from "@/lib/contracts";
import type { ResettleOrderProfitPayload, SettleOrderProfitPayload } from "@/services/order-profit-server-service";

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

export async function submitResettleOrderProfit(payload: ResettleOrderProfitPayload): Promise<OrderProfitRecord> {
  const response = await fetch("/api/finance/order-profits/resettle", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-order-profit-resettle-${crypto.randomUUID()}` },
    body: JSON.stringify(payload),
  });
  const data = await response.json().catch(() => null) as { settlement?: OrderProfitRecord; message?: string } | null;
  if (!response.ok || !data?.settlement) throw new Error(data?.message ?? "订单利润重算失败，请重试");
  return data.settlement;
}

export async function fetchOrderProfitHistory(salesOrderId: string): Promise<OrderProfitRecord[]> {
  const response = await fetch(`/api/finance/order-profits/history?salesOrderId=${encodeURIComponent(salesOrderId)}`, {
    method: "GET",
  });
  const data = await response.json().catch(() => null) as { history?: OrderProfitRecord[]; message?: string } | null;
  if (!response.ok || !data?.history) throw new Error(data?.message ?? "加载利润历史版本失败");
  return data.history;
}
