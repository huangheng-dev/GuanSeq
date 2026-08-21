import { z } from "zod";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { createSalesShipment } from "@/services/sales-shipment-server-service";

const line = z.object({ orderLineId: z.string().uuid(), shippedQuantity: z.number().positive() });
const schema = z.object({
  salesOrderId: z.string().uuid(),
  warehouseId: z.string().uuid(),
  plannedShippingDate: z.string().min(1),
  note: z.string().max(500).nullable().optional(),
  lines: z.array(line).min(1).max(100),
});

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();
  const parsed = schema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "销售发货参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await createSalesShipment(parsed.data, requestId);
    return Response.json(result, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) {
    const status = error instanceof GuanSeqApiError ? error.status : 500;
    return Response.json({ message: error instanceof Error ? error.message : "销售发货服务发生未预期错误", requestId }, { status, headers: { "X-Request-Id": requestId } });
  }
}