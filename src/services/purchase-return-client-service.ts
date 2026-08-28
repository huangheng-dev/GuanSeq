import type { PurchaseReturnRecord } from "@/lib/contracts";
import type { PurchaseReturnMutation } from "@/services/purchase-return-server-service";

export async function submitPurchaseReturn(input: PurchaseReturnMutation): Promise<PurchaseReturnRecord> {
  const response = await fetch("/api/procurement/returns/mutate", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) });
  const data = await response.json().catch(() => null) as { record?: PurchaseReturnRecord; message?: string } | null;
  if (!response.ok || !data?.record) throw new Error(data?.message ?? "采购退货动作失败，请重试");
  return data.record;
}
