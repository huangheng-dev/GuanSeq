import type { ReceivableCreditNoteRecord, ReceivableInvoiceRecord } from "@/lib/contracts";
import type {
  CreateReceivableCreditNotePayload,
  CreateReceivableInvoicePayload,
  PostReceivableReceiptPayload,
  PostReceivableRefundPayload,
  ReverseReceivableReceiptPayload,
} from "@/services/receivable-server-service";

type MutateResponse = {
  invoice?: ReceivableInvoiceRecord;
  creditNote?: ReceivableCreditNoteRecord;
  message?: string;
};

async function postMutation(
  operation: string,
  payload: unknown,
): Promise<MutateResponse> {
  const response = await fetch("/api/finance/receivables/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-receivable-${crypto.randomUUID()}` },
    body: JSON.stringify({ operation, payload }),
  });
  const data = await response.json().catch(() => null) as MutateResponse | null;
  if (!response.ok || (!data?.invoice && !data?.creditNote)) {
    throw new Error(data?.message ?? "应收业务处理失败，请重试");
  }
  return data;
}

export async function submitCreateReceivableInvoice(payload: CreateReceivableInvoicePayload): Promise<ReceivableInvoiceRecord> {
  const data = await postMutation("createInvoice", payload);
  return data.invoice!;
}

export async function submitPostReceivableReceipt(payload: PostReceivableReceiptPayload): Promise<ReceivableInvoiceRecord> {
  const data = await postMutation("postReceipt", payload);
  return data.invoice!;
}

export async function submitCreateReceivableCreditNote(payload: CreateReceivableCreditNotePayload): Promise<ReceivableCreditNoteRecord> {
  const data = await postMutation("createCreditNote", payload);
  return data.creditNote!;
}

export async function submitPostReceivableRefund(payload: PostReceivableRefundPayload): Promise<ReceivableInvoiceRecord> {
  const data = await postMutation("postRefund", payload);
  return data.invoice!;
}

export async function submitReverseReceivableReceipt(payload: ReverseReceivableReceiptPayload): Promise<ReceivableInvoiceRecord> {
  const data = await postMutation("reverseReceipt", payload);
  return data.invoice!;
}
