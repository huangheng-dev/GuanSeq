import type { InventoryRecord } from "@/lib/contracts";
import type { InventoryMovementInput } from "@/services/inventory-server-service";

export async function submitInventoryMovement(input: InventoryMovementInput, requestId: string): Promise<InventoryRecord> {
  const response = await fetch("/api/warehouse/inventory", { method: "POST", headers: { "Content-Type": "application/json", "X-Request-Id": requestId }, body: JSON.stringify(input) });
  const payload = await response.json().catch(() => null) as { balance?: InventoryRecord; message?: string } | null;
  if (!response.ok || !payload?.balance) throw new Error(payload?.message ?? "库存事务过账失败，请重试");
  return payload.balance;
}
