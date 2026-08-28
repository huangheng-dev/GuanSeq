import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { mutateSalesReturn } from "@/services/sales-return-server-service";

const create = z.object({ operation: z.literal("create"), salesOrderId: z.string().uuid(), expectedOrderVersion: z.number().int().nonnegative(),
  returnDate: z.string().min(1), reason: z.string().min(4).max(500), note: z.string().max(500).nullable().optional(),
  lines: z.array(z.object({ orderLineId: z.string().uuid(), returnQuantity: z.number().positive() })).min(1).max(100) });
const action = z.object({ operation: z.literal("action"), id: z.string().uuid(), action: z.enum(["CANCEL", "RECEIVE", "INSPECT", "REVERSE_RECEIPT"]),
  expectedVersion: z.number().int().nonnegative(), reason: z.string().min(4).max(500), warehouseId: z.string().uuid().nullable().optional(),
  locationId: z.string().uuid().nullable().optional(), lines: z.array(z.object({ returnLineId: z.string().uuid(), lotNumber: z.string().max(80).nullable().optional(),
    acceptedQuantity: z.number().nonnegative().nullable().optional(), rejectedQuantity: z.number().nonnegative().nullable().optional() })).max(100).optional() });

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = z.discriminatedUnion("operation", [create, action]).safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "销售退货参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateSalesReturn(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "销售退货服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
