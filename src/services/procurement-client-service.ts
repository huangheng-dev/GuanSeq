import type { PurchaseOrderRecord } from "@/lib/contracts";
import type { PurchaseOrderMutation } from "@/services/procurement-server-service";

export async function submitPurchaseOrderMutation(input: PurchaseOrderMutation): Promise<PurchaseOrderRecord> {
  const response = await fetch("/api/procurement/orders/mutate", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": `web-purchase-${crypto.randomUUID()}` }, body: JSON.stringify(input) });
  const payload = await response.json().catch(() => null) as { order?: PurchaseOrderRecord; message?: string } | null;
  if (!response.ok || !payload?.order) throw new Error(payload?.message ?? "采购订单操作失败，请重试");
  return payload.order;
}
