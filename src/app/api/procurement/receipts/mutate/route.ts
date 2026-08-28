import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { createPurchaseReceipt } from "@/services/purchase-receipt-server-service";

const line = z.object({ orderLineId: z.string().uuid(), receivedQuantity: z.number().positive(), lotNumber: z.string().min(1).max(80) });
const schema = z.object({
  purchaseOrderId: z.string().uuid(),
  warehouseId: z.string().uuid(),
  locationId: z.string().uuid(),
  note: z.string().max(500).nullable().optional(),
  source: z.enum(["DESKTOP_FORM", "MOBILE_SCAN"]).optional(),
  lines: z.array(line).min(1).max(100),
});

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = schema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "采购收货参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await createPurchaseReceipt(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "采购收货服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}
