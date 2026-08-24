import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import {
  createPayableCreditNote,
  createPayableInvoice,
  postPayablePayment,
  postPayableRefund,
  reversePayablePayment,
} from "@/services/payable-server-service";

const createSchema = z.object({
  purchaseOrderId: z.string().uuid(), supplierInvoiceNumber: z.string().min(1).max(80),
  invoiceDate: z.string(), dueDate: z.string(),
  lines: z.array(z.object({ purchaseOrderLineId: z.string().uuid(), invoiceQuantity: z.number().positive() })).min(1),
  advanceId: z.string().uuid().nullable().optional(),
});
const paymentSchema = z.object({
  invoiceId: z.string().uuid(), expectedVersion: z.number().int().nonnegative(),
  paymentDate: z.string(), amount: z.number().positive(),
  paymentMethod: z.enum(["BANK_TRANSFER", "CASH", "BILL", "OTHER"]),
  bankReference: z.string().nullable(), note: z.string().nullable(),
});
const creditNoteSchema = z.object({
  originalInvoiceId: z.string().uuid(),
  supplierCreditNoteNumber: z.string().max(80).nullable().optional(),
  taxNoticeNumber: z.string().max(80).nullable().optional(),
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
  paymentId: z.string().uuid(), reversalDate: z.string(), reason: z.string().min(4).max(500),
});

const requestSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("createInvoice"), payload: createSchema }),
  z.object({ operation: z.literal("postPayment"), payload: paymentSchema }),
  z.object({ operation: z.literal("createCreditNote"), payload: creditNoteSchema }),
  z.object({ operation: z.literal("postRefund"), payload: refundSchema }),
  z.object({ operation: z.literal("reversePayment"), payload: reverseSchema }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = requestSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json({ message: "应付业务参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  }
  try {
    let result;
    switch (parsed.data.operation) {
      case "createInvoice":
        result = await createPayableInvoice(parsed.data.payload, requestId);
        return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
      case "postPayment":
        result = await postPayablePayment(parsed.data.payload, requestId);
        return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
      case "createCreditNote": {
        const creditNote = await createPayableCreditNote(parsed.data.payload, requestId);
        return Response.json({ creditNote }, { headers: { "X-Request-Id": requestId } });
      }
      case "postRefund": {
        const invoice = await postPayableRefund(parsed.data.payload, requestId);
        return Response.json({ invoice }, { headers: { "X-Request-Id": requestId } });
      }
      case "reversePayment": {
        const invoice = await reversePayablePayment(parsed.data.payload, requestId);
        return Response.json({ invoice }, { headers: { "X-Request-Id": requestId } });
      }
    }
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json(
      { message: error instanceof Error ? error.message : "应付服务发生未预期错误", requestId },
      { status, headers: { "X-Request-Id": requestId } },
    );
  }
}
