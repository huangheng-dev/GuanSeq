import "server-only";

import { randomUUID } from "node:crypto";
import {
  payableCreditNotePageSchema,
  payableCreditNoteSchema,
  payableInvoicePageSchema,
  payableInvoiceRecordSchema,
  payableReferenceDataSchema,
  type PayableCreditNoteRecord,
  type Advance,
  type PayableInvoiceRecord,
} from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";
import { listAdvances } from "./advance-server-service";

const paths = new Set(["/finance/payables", "/finance/purchase-settlement/invoices", "/finance/purchase-settlement/payments"]);

export type PayablePageData = {
  source: "backend";
  invoices: PayableInvoiceRecord[];
  references: ReturnType<typeof payableReferenceDataSchema.parse>;
  advances: Advance[];
};

export type CreatePayableInvoicePayload = {
  purchaseOrderId: string;
  supplierInvoiceNumber: string;
  invoiceDate: string;
  dueDate: string;
  lines: Array<{ purchaseOrderLineId: string; invoiceQuantity: number }>;
  advanceId?: string | null;
};

export type PostPayablePaymentPayload = {
  invoiceId: string;
  expectedVersion: number;
  paymentDate: string;
  amount: number;
  paymentMethod: "BANK_TRANSFER" | "CASH" | "BILL" | "OTHER";
  bankReference: string | null;
  note: string | null;
};

export type CreatePayableCreditNotePayload = {
  originalInvoiceId: string;
  supplierCreditNoteNumber?: string | null;
  taxNoticeNumber?: string | null;
  creditNoteDate: string;
  dueDate: string;
  reason: string;
  lines: Array<{ originalInvoiceLineId: string; creditQuantity: number; unitPrice?: number | null }>;
};

export type PostPayableRefundPayload = {
  invoiceId: string;
  expectedVersion: number;
  refundDate: string;
  amount: number;
  paymentMethod: "BANK_TRANSFER" | "CASH" | "BILL" | "OTHER";
  bankReference: string | null;
  note: string | null;
};

export type ReversePayablePaymentPayload = {
  paymentId: string;
  reversalDate: string;
  reason: string;
};

export async function getPayablePageData(pathname: string): Promise<PayablePageData | null> {
  if (!paths.has(pathname)) return null;
  const requestId = `web-payable-list-${randomUUID()}`;
  const [invoiceResponse, referenceResponse, advanceResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/finance/payable-invoices?page=0&size=100&status=ALL", requestId),
    requestGuanSeqApi("/api/v1/finance/payable-reference-data", requestId),
    listAdvances({ type: "PAYABLE", status: "ALL", page: 0, size: 100 }),
  ]);
  if (!invoiceResponse) return null;
  if (invoiceResponse && !invoiceResponse.ok) await readApiError(invoiceResponse, "应付发票台账加载失败");
  if (!referenceResponse) return null;
  if (referenceResponse && !referenceResponse.ok) await readApiError(referenceResponse, "应付参考数据加载失败");
  return {
    source: "backend",
    invoices: payableInvoicePageSchema.parse(await invoiceResponse.json()).items,
    references: payableReferenceDataSchema.parse(await referenceResponse.json()),
    advances: advanceResponse.items.filter((item) => item.availableBalance > 0),
  };
}

export async function createPayableInvoice(payload: CreatePayableInvoicePayload, requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/finance/payable-invoices", requestId, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
  });
  if (!response) throw new GuanSeqApiError("应付开票服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "采购发票暂时无法创建");
  return { invoice: payableInvoiceRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function postPayablePayment(payload: PostPayablePaymentPayload, requestId: string) {
  const { invoiceId, ...body } = payload;
  const response = await requestGuanSeqApi(`/api/v1/finance/payable-invoices/${invoiceId}/payments`, requestId, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  if (!response) throw new GuanSeqApiError("付款核销服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "付款暂时无法登记");
  return { invoice: payableInvoiceRecordSchema.parse(await response.json()), requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function listPayableCreditNotes(requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/finance/payable-credit-notes?page=0&size=100", requestId);
  if (!response) return [];
  if (!response.ok) await readApiError(response, "红字发票列表加载失败");
  return payableCreditNotePageSchema.parse(await response.json()).items;
}

export async function createPayableCreditNote(payload: CreatePayableCreditNotePayload, requestId: string) {
  const response = await requestGuanSeqApi("/api/v1/finance/payable-credit-notes", requestId, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
  });
  if (!response) throw new GuanSeqApiError("红字发票服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "红字发票暂时无法开具");
  return payableCreditNoteSchema.parse(await response.json()) as PayableCreditNoteRecord;
}

export async function postPayableRefund(payload: PostPayableRefundPayload, requestId: string) {
  const { invoiceId, ...body } = payload;
  const response = await requestGuanSeqApi(`/api/v1/finance/payable-invoices/${invoiceId}/refunds`, requestId, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  if (!response) throw new GuanSeqApiError("退款服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "退款暂时无法登记");
  return payableInvoiceRecordSchema.parse(await response.json()) as PayableInvoiceRecord;
}

export async function reversePayablePayment(payload: ReversePayablePaymentPayload, requestId: string) {
  const { paymentId, ...body } = payload;
  const response = await requestGuanSeqApi(`/api/v1/finance/payables/payments/${paymentId}/reverse`, requestId, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  if (!response) throw new GuanSeqApiError("反核销服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "反核销暂时无法执行");
  return payableInvoiceRecordSchema.parse(await response.json()) as PayableInvoiceRecord;
}
