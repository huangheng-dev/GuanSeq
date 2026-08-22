import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import {
  createReceivableCreditNote,
  createReceivableInvoice,
  postReceivableReceipt,
  postReceivableRefund,
  reverseReceivableReceipt,
} from "@/services/receivable-server-service";

const createSchema = z.object({
  salesOrderId: z.string().uuid(), invoiceDate: z.string(), dueDate: z.string(),
  lines: z.array(z.object({ salesOrderLineId: z.string().uuid(), invoiceQuantity: z.number().positive() })).min(1),
});
const receiptSchema = z.object({
  invoiceId: z.string().uuid(), expectedVersion: z.number().int().nonnegative(),
  receiptDate: z.string(), amount: z.number().positive(),
  paymentMethod: z.enum(["BANK_TRANSFER", "CASH", "BILL", "OTHER"]),
  bankReference: z.string().nullable(), note: z.string().nullable(),
});
const creditNoteSchema = z.object({
  originalInvoiceId: z.string().uuid(), taxNoticeNumber: z.string().nullable().optional(),
  creditNoteDate: z.string(), dueDate: z.string(), reason: z.string().min(4).max(500),
  lines: z.array(z.object({
    originalInvoiceLineId: z.string().uuid(),
    creditQuantity: z.number().positive(),
    unitPrice: z.number().nonnegative().nullable().optional(),
  })).min(1).max(100),
});
const refundSchema = z.object({
  invoiceId: z.string().uuid(), expectedVersion: z.number().int().nonnegative(),
  refundDate: z.string(), amount: z.number().positive(),
  paymentMethod: z.enum(["BANK_TRANSFER", "CASH", "BILL", "OTHER"]),
  bankReference: z.string().nullable(), note: z.string().nullable(),
});
const reverseSchema = z.object({
  receiptId: z.string().uuid(), reversalDate: z.string(), reason: z.string().min(4).max(500),
});

const requestSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("createInvoice"), payload: createSchema }),
  z.object({ operation: z.literal("postReceipt"), payload: receiptSchema }),
  z.object({ operation: z.literal("createCreditNote"), payload: creditNoteSchema }),
  z.object({ operation: z.literal("postRefund"), payload: refundSchema }),
  z.object({ operation: z.literal("reverseReceipt"), payload: reverseSchema }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = requestSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json({ message: "应收业务参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  }
  try {
    let result;
    switch (parsed.data.operation) {
      case "createInvoice":
        result = await createReceivableInvoice(parsed.data.payload, requestId);
        return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
      case "postReceipt":
        result = await postReceivableReceipt(parsed.data.payload, requestId);
        return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
      case "createCreditNote": {
        const creditNote = await createReceivableCreditNote(parsed.data.payload, requestId);
        return Response.json({ creditNote }, { headers: { "X-Request-Id": requestId } });
      }
      case "postRefund": {
        const invoice = await postReceivableRefund(parsed.data.payload, requestId);
        return Response.json({ invoice }, { headers: { "X-Request-Id": requestId } });
      }
      case "reverseReceipt": {
        const invoice = await reverseReceivableReceipt(parsed.data.payload, requestId);
        return Response.json({ invoice }, { headers: { "X-Request-Id": requestId } });
      }
    }
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      { message: error instanceof Error ? error.message : "应收服务发生未预期错误", requestId },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
