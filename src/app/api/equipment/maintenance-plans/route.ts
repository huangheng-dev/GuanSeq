import { z } from "zod";

import { equipmentMaintenancePlanActionSchema, equipmentWorkOrderPrioritySchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { loadEquipmentMaintenancePlan, loadEquipmentMaintenancePlanPage, mutateEquipmentMaintenancePlan } from "@/services/equipment-maintenance-plan-server-service";

const mutationSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("createPlan"), planCode: z.string().trim().regex(/^[A-Z0-9][A-Z0-9_-]{1,39}$/),
    name: z.string().trim().min(1).max(160), workType: z.enum(["INSPECTION", "PREVENTIVE_MAINTENANCE"]),
    assetId: z.string().uuid(), description: z.string().trim().min(4).max(1000), priority: equipmentWorkOrderPrioritySchema,
    intervalDays: z.number().int().min(1).max(3650), leadDays: z.number().int().min(0).max(365),
    firstDueDate: z.string().date(), plannedStartTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/),
    dueTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/), assignee: z.string().trim().min(1).max(80),
    reason: z.string().trim().min(4).max(500), assetExpectedVersion: z.number().int().nonnegative() }),
  z.object({ operation: z.literal("planAction"), id: z.string().uuid(), action: equipmentMaintenancePlanActionSchema,
    reason: z.string().trim().min(4).max(500), expectedVersion: z.number().int().nonnegative() }),
  z.object({ operation: z.literal("generateDue"), asOfDate: z.string().date(), reason: z.string().trim().min(4).max(500) }),
]);

function requestIdFrom(request: Request) { return request.headers.get("X-Request-Id") ?? crypto.randomUUID(); }
function errorResponse(error: unknown, requestId: string, fallback: string) {
  const status = error instanceof GuanSeqApiError ? error.status : 500;
  return Response.json({ message: error instanceof Error ? error.message : fallback, requestId },
    { status, headers: { "X-Request-Id": requestId } });
}

export async function GET(request: Request) {
  const requestId = requestIdFrom(request);
  try {
    const id = new URL(request.url).searchParams.get("id");
    if (id) {
      const parsedId = z.string().uuid().safeParse(id);
      if (!parsedId.success) return Response.json({ message: "周期维护模板编号参数无效", requestId },
        { status: 400, headers: { "X-Request-Id": requestId } });
      const detail = await loadEquipmentMaintenancePlan(parsedId.data, requestId);
      return Response.json({ plan: detail.plan }, { headers: { "X-Request-Id": detail.requestId } });
    }
    const result = await loadEquipmentMaintenancePlanPage(requestId);
    if (result.source === "unavailable") return Response.json({ message: result.message, requestId: result.requestId },
      { status: result.status, headers: { "X-Request-Id": result.requestId } });
    return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "周期维护计划服务发生未预期错误"); }
}

export async function POST(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "周期维护参数无效，请检查周期、提前期、时间、原因和版本", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateEquipmentMaintenancePlan(parsed.data, requestId);
    return Response.json(result.generation ? { generation: result.generation } : { plan: result.plan },
      { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "周期维护计划服务发生未预期错误"); }
}
