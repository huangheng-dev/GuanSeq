import type { SalesOrderRecord } from "@/lib/contracts";
import type { SalesOrderMutation } from "@/services/sales-order-server-service";

export async function submitSalesOrderMutation(input: SalesOrderMutation): Promise<SalesOrderRecord> {
  const response = await fetch("/api/sales/orders/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-sales-${crypto.randomUUID()}` },
    body: JSON.stringify(input),
  });
  const payload = await response.json().catch(() => null) as { order?: SalesOrderRecord; message?: string } | null;
  if (!response.ok || !payload?.order) throw new Error(payload?.message ?? "销售订单操作失败，请重试");
  return payload.order;
}
