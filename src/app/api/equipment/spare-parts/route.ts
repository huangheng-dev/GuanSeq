import { z } from "zod";

import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { createEquipmentSparePart, loadEquipmentSparePartPage } from "@/services/equipment-spare-part-server-service";

const mutationSchema = z.object({ materialId: z.string().uuid(), preferredWarehouseId: z.string().uuid(),
  reorderPoint: z.number().nonnegative(), reason: z.string().trim().min(4).max(500) });
function requestIdFrom(request: Request) { return request.headers.get("X-Request-Id") ?? crypto.randomUUID(); }
function errorResponse(error: unknown, requestId: string) { const status = error instanceof GuanSeqApiError ? error.status : 500;
  return Response.json({ message: error instanceof Error ? error.message : "设备备件服务发生未预期错误", requestId },
    { status, headers: { "X-Request-Id": requestId } }); }

export async function GET(request: Request) {
  const requestId = requestIdFrom(request);
  try { const result = await loadEquipmentSparePartPage(requestId);
    if (result.source === "unavailable") return Response.json({ message: result.message, requestId: result.requestId },
      { status: result.status, headers: { "X-Request-Id": result.requestId } });
    return Response.json({ page: result.page, references: result.references }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId); }
}
export async function POST(request: Request) {
  const requestId = requestIdFrom(request); const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "备件参数无效，请检查物料、仓库、安全库存和原因", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } });
  try { const result = await createEquipmentSparePart(parsed.data, requestId);
    return Response.json({ sparePart: result.sparePart }, { status: 201, headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId); }
}
