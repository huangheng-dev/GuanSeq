import type { ProductionOrderRecord } from "@/lib/contracts";
import type { ProductionOrderMutation } from "@/services/production-order-server-service";

export async function submitProductionOrderMutation(input: ProductionOrderMutation): Promise<ProductionOrderRecord> {
  const response = await fetch("/api/production/orders/mutate", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": `web-production-${crypto.randomUUID()}` }, body: JSON.stringify(input) });
  const payload = await response.json().catch(() => null) as { order?: ProductionOrderRecord; message?: string } | null;
  if (!response.ok || !payload?.order) throw new Error(payload?.message ?? "生产订单操作失败，请重试");
  return payload.order;
}
