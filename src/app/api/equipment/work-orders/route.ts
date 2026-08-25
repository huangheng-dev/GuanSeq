import { z } from "zod";

import { equipmentWorkOrderActionSchema, equipmentWorkOrderOutcomeSchema, equipmentWorkOrderPrioritySchema, equipmentWorkTypeSchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { loadEquipmentWorkOrder, loadEquipmentWorkOrderPage, mutateEquipmentWorkOrder } from "@/services/equipment-work-order-server-service";

const mutationSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("create"), assetId: z.string().uuid(), workType: equipmentWorkTypeSchema,
    title: z.string().trim().min(1).max(160), description: z.string().trim().min(4).max(1000),
    priority: equipmentWorkOrderPrioritySchema, plannedStartAt: z.string().datetime(), dueAt: z.string().datetime(),
    assignee: z.string().trim().min(1).max(80), reason: z.string().trim().min(4).max(500),
    assetExpectedVersion: z.number().int().nonnegative() }),
  z.object({ operation: z.literal("action"), id: z.string().uuid(), action: equipmentWorkOrderActionSchema,
    reason: z.string().trim().min(4).max(500), expectedVersion: z.number().int().nonnegative(),
    assetExpectedVersion: z.number().int().nonnegative(), outcome: equipmentWorkOrderOutcomeSchema.nullable().optional(),
    completionNotes: z.string().trim().max(1000).nullable().optional() }),
]);

function requestIdFrom(request: Request) { return request.headers.get("X-Request-Id") ?? crypto.randomUUID(); }
function errorResponse(error: unknown, requestId: string, fallback: string) {
  const status = error instanceof GuanSeqApiError ? error.status : 500;
  return Response.json({ message: error instanceof Error ? error.message : fallback, requestId },
    { status, headers: { "X-Request-Id": requestId } });
}

export async function GET(request: Request) {
  const requestId = requestIdFrom(request);
  const id = new URL(request.url).searchParams.get("id");
  try {
    if (id) {
      const parsedId = z.string().uuid().safeParse(id);
      if (!parsedId.success) return Response.json({ message: "运维工单编号参数无效", requestId }, { status: 400, headers: { "X-Request-Id": requestId } });
      const result = await loadEquipmentWorkOrder(parsedId.data, requestId);
      return Response.json({ workOrder: result.workOrder }, { headers: { "X-Request-Id": result.requestId } });
    }
    const result = await loadEquipmentWorkOrderPage(requestId);
    if (result.source === "unavailable") return Response.json({ message: result.message, requestId: result.requestId },
      { status: result.status, headers: { "X-Request-Id": result.requestId } });
    return Response.json({ page: result.page, assets: result.assets }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备运维服务发生未预期错误"); }
}

export async function POST(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "设备运维参数无效，请检查必填项、时间、原因和版本", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateEquipmentWorkOrder(parsed.data, requestId);
    return Response.json({ workOrder: result.workOrder }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备运维服务发生未预期错误"); }
}
