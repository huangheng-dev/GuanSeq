import type { PayableCreditNoteRecord, PayableInvoiceRecord } from "@/lib/contracts";
import type {
  CreatePayableCreditNotePayload,
  CreatePayableInvoicePayload,
  PostPayablePaymentPayload,
  PostPayableRefundPayload,
  ReversePayablePaymentPayload,
} from "@/services/payable-server-service";

type MutateResponse = {
  invoice?: PayableInvoiceRecord;
  creditNote?: PayableCreditNoteRecord;
  message?: string;
};

async function postMutation(
  operation: string,
  payload: unknown,
): Promise<MutateResponse> {
  const response = await fetch("/api/finance/payables/mutate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Request-Id": `web-payable-${crypto.randomUUID()}` },
    body: JSON.stringify({ operation, payload }),
  });
  const data = await response.json().catch(() => null) as MutateResponse | null;
  if (!response.ok || (!data?.invoice && !data?.creditNote)) {
    throw new Error(data?.message ?? "应付业务处理失败，请重试");
  }
  return data;
}

export async function submitCreatePayableInvoice(payload: CreatePayableInvoicePayload): Promise<PayableInvoiceRecord> {
  const data = await postMutation("createInvoice", payload);
  return data.invoice!;
}

export async function submitPostPayablePayment(payload: PostPayablePaymentPayload): Promise<PayableInvoiceRecord> {
  const data = await postMutation("postPayment", payload);
  return data.invoice!;
}

export async function submitCreatePayableCreditNote(payload: CreatePayableCreditNotePayload): Promise<PayableCreditNoteRecord> {
  const data = await postMutation("createCreditNote", payload);
  return data.creditNote!;
}

export async function submitPostPayableRefund(payload: PostPayableRefundPayload): Promise<PayableInvoiceRecord> {
  const data = await postMutation("postRefund", payload);
  return data.invoice!;
}

export async function submitReversePayablePayment(payload: ReversePayablePaymentPayload): Promise<PayableInvoiceRecord> {
  const data = await postMutation("reversePayment", payload);
  return data.invoice!;
}
