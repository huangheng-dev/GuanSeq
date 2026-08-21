import "server-only";

import { randomUUID } from "node:crypto";
import { receivableInvoicePageSchema, receivableInvoiceRecordSchema, receivableReferenceDataSchema, type ReceivableInvoiceRecord } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

const paths = new Set(["/finance/receivables", "/finance/sales-settlement/invoicing", "/finance/sales-settlement/receipts"]);

export type ReceivablePageData = {
  source: "backend";
  invoices: ReceivableInvoiceRecord[];
  references: ReturnType<typeof receivableReferenceDataSchema.parse>;
};

export type CreateReceivableInvoicePayload = {
  salesOrderId: string;
  invoiceDate: string;
  dueDate: string;
  lines: Array<{ salesOrderLineId: string; invoiceQuantity: number }>;
};

export type PostReceivableReceiptPayload = {
  invoiceId: string;
  expectedVersion: number;
  receiptDate: string;
  amount: number;
  paymentMethod: "BANK_TRANSFER" | "CASH" | "BILL" | "OTHER";
  bankReference: string | null;
  note: string | null;
};

export async function getReceivablePageData(pathname: string): Promise<ReceivablePageData | null> {
  if (!paths.has(pathname)) return null;
  const requestId = `web-receivable-list-${randomUUID()}`;
  const [invoiceResponse, referenceResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/finance/receivable-invoices?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/finance/receivable-reference-data", requestId),
  ]);
  if (!invoiceResponse?.ok || !referenceResponse?.ok) return null;
  return {
    source: "backend",
    invoices: receivableInvoicePageSchema.parse(await invoiceResponse.json()).items,
    references: receivableReferenceDataSchema.parse(await referenceResponse.json()),
  };
}

export async function createReceivableInvoice(payload: CreateReceivableInvoicePayload, requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/finance/receivable-invoices", requestId, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
  });
  if (!response) throw new GuanSeqApiError("应收开票服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "应收发票暂时无法创建");
  return { invoice: receivableInvoiceRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function postReceivableReceipt(payload: PostReceivableReceiptPayload, requestId: string) {
  const { invoiceId, ...body } = payload;
  const response = await requestGuanSeqApi(`/api/v1/finance/receivable-invoices/${invoiceId}/receipts`, requestId, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  if (!response) throw new GuanSeqApiError("收款核销服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "收款暂时无法登记");
  return { invoice: receivableInvoiceRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}
