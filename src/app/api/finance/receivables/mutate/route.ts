import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { createReceivableInvoice, postReceivableReceipt } from "@/services/receivable-server-service";

const createSchema = z.object({ salesOrderId: z.string().uuid(), invoiceDate: z.string(), dueDate: z.string(), lines: z.array(z.object({ salesOrderLineId: z.string().uuid(), invoiceQuantity: z.number().positive() })).min(1) });
const receiptSchema = z.object({ invoiceId: z.string().uuid(), expectedVersion: z.number().int().nonnegative(), receiptDate: z.string(), amount: z.number().positive(), paymentMethod: z.enum(["BANK_TRANSFER", "CASH", "BILL", "OTHER"]), bankReference: z.string().nullable(), note: z.string().nullable() });
const requestSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("createInvoice"), payload: createSchema }),
  z.object({ operation: z.literal("postReceipt"), payload: receiptSchema }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = requestSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "应收业务参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = parsed.data.operation === "createInvoice"
      ? await createReceivableInvoice(parsed.data.payload, requestId)
      : await postReceivableReceipt(parsed.data.payload, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "应收服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
