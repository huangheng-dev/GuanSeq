import type { ReceivableInvoiceRecord } from "@/lib/contracts";
import type { CreateReceivableInvoicePayload, PostReceivableReceiptPayload } from "@/services/receivable-server-service";

async function mutate(operation: "createInvoice" | "postReceipt", payload: CreateReceivableInvoicePayload | PostReceivableReceiptPayload): Promise<ReceivableInvoiceRecord> {
  const response = await fetch("/api/finance/receivables/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-receivable-${crypto.randomUUID()}` },
    body: JSON.stringify({ operation, payload }),
  });
  const data = await response.json().catch(() => null) as { invoice?: ReceivableInvoiceRecord; message?: string } | null;
  if (!response.ok || !data?.invoice) throw new Error(data?.message ?? "应收业务处理失败，请重试");
  return data.invoice;
}

export function submitCreateReceivableInvoice(payload: CreateReceivableInvoicePayload) {
  return mutate("createInvoice", payload);
}

export function submitPostReceivableReceipt(payload: PostReceivableReceiptPayload) {
  return mutate("postReceipt", payload);
}
