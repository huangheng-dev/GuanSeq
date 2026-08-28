import type { PurchaseReceiptRecord } from "@/lib/contracts";
import type { CreatePurchaseReceiptPayload } from "@/services/purchase-receipt-server-service";

export async function submitCreatePurchaseReceipt(payload: CreatePurchaseReceiptPayload, stableRequestId?: string): Promise<PurchaseReceiptRecord> {
  const response = await fetch("/api/procurement/receipts/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": stableRequestId ?? `web-receipt-${crypto.randomUUID()}` },
    body: JSON.stringify(payload),
  });
  const data = await response.json().catch(() => null) as { receipt?: PurchaseReceiptRecord; message?: string } | null;
  if (!response.ok || !data?.receipt) throw new Error(data?.message ?? "采购收货登记失败，请重试");
  return data.receipt;
}
