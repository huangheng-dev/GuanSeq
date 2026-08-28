import { z } from "zod";

import { equipmentOeeActionSchema, equipmentOeeDowntimeCategorySchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { loadEquipmentOeePage, loadEquipmentOeeRecord, mutateEquipmentOee } from "@/services/equipment-oee-server-service";

const createSchema = z.object({ operation: z.literal("create"), assetId: z.string().uuid(),
  windowStart: z.string().datetime(), windowEnd: z.string().datetime(), plannedProductionMinutes: z.number().positive(),
  idealCycleSeconds: z.number().positive(), totalCount: z.number().int().nonnegative(), goodCount: z.number().int().nonnegative(),
  shiftName: z.string().trim().min(1).max(80), productionReference: z.string().trim().max(120).nullable().optional(),
  sourceReference: z.string().trim().max(160).nullable().optional(), reason: z.string().trim().min(4).max(500) });
const actSchema = z.object({ operation: z.literal("act"), id: z.string().uuid(), action: equipmentOeeActionSchema,
  reason: z.string().trim().min(4).max(500), expectedVersion: z.number().int().nonnegative(),
  windowStart: z.string().datetime().optional(), windowEnd: z.string().datetime().optional(),
  plannedProductionMinutes: z.number().positive().optional(), idealCycleSeconds: z.number().positive().optional(),
  totalCount: z.number().int().nonnegative().optional(), goodCount: z.number().int().nonnegative().optional(),
  shiftName: z.string().trim().min(1).max(80).optional(), productionReference: z.string().trim().max(120).nullable().optional(),
  sourceReference: z.string().trim().max(160).nullable().optional(), downtimeId: z.string().uuid().optional(),
  downtimeStartedAt: z.string().datetime().optional(), downtimeEndedAt: z.string().datetime().optional(),
  reasonCategory: equipmentOeeDowntimeCategorySchema.optional(), responsibleParty: z.string().trim().min(1).max(80).optional(),
  description: z.string().trim().min(4).max(500).optional() }).superRefine((input, context) => {
    if (input.action === "UPDATE" && (!input.windowStart || !input.windowEnd || input.plannedProductionMinutes == null
        || input.idealCycleSeconds == null || input.totalCount == null || input.goodCount == null || !input.shiftName))
      context.addIssue({ code: "custom", message: "修改 OEE 必须提交完整统计口径" });
    if (["ADD_DOWNTIME", "UPDATE_DOWNTIME"].includes(input.action)
        && (!input.downtimeStartedAt || !input.downtimeEndedAt || !input.reasonCategory || !input.responsibleParty || !input.description))
      context.addIssue({ code: "custom", message: "停机事件必须填写完整" });
    if (["UPDATE_DOWNTIME", "REMOVE_DOWNTIME"].includes(input.action) && !input.downtimeId)
      context.addIssue({ code: "custom", message: "停机动作必须选择停机事件" });
  });
const mutationSchema = z.union([createSchema, actSchema]);

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
      const parsed = z.string().uuid().safeParse(id);
      if (!parsed.success) return Response.json({ message: "OEE 记录编号无效", requestId },
        { status: 400, headers: { "X-Request-Id": requestId } });
      const result = await loadEquipmentOeeRecord(parsed.data, requestId);
      return Response.json({ record: result.record }, { headers: { "X-Request-Id": result.requestId } });
    }
    const result = await loadEquipmentOeePage(requestId);
    if (result.source === "unavailable") return Response.json({ message: result.message, requestId: result.requestId },
      { status: result.status, headers: { "X-Request-Id": result.requestId } });
    return Response.json({ data: result }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "OEE 服务发生未预期错误"); }
}

export async function POST(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "OEE 参数无效，请检查时间、产量、停机和版本", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateEquipmentOee(parsed.data, requestId);
    return Response.json({ record: result.record }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "OEE 服务发生未预期错误"); }
}
