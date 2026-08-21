import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutateMrpSuggestion } from "@/services/mrp-suggestion-server-service";

const mutation = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("action"), id: z.string().uuid(), action: z.enum(["APPROVE", "REJECT"]), expectedVersion: z.number().int().nonnegative(), comment: z.string().max(500).optional() }),
  z.object({ operation: z.literal("convert"), id: z.string().uuid(), expectedVersion: z.number().int().nonnegative(), supplierId: z.string().uuid().optional(), currency: z.enum(["CNY", "USD", "EUR"]).optional(), taxRate: z.number().min(0).max(1).optional(), unitPrice: z.number().nonnegative().optional(), requestedReceiptDate: z.string().optional(), buyer: z.string().max(80).optional(), plannedStartDate: z.string().optional(), plannedReceiptDate: z.string().optional(), workshop: z.string().max(120).optional(), owner: z.string().max(80).optional() }),
]);

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = mutation.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "MRP 建议操作参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try { const result = await mutateMrpSuggestion(parsed.data, requestId); return Response.json(result, { headers: { "X-Request-Id": result.requestId } }); }
  catch (error) { const status = error instanceof GuanSeqApiError ? error.status : 500; return Response.json({ message: error instanceof Error ? error.message : "MRP 建议服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } }); }
}
