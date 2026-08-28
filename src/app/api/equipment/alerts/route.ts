import { z } from "zod";

import { equipmentAlertRuleTypeSchema, equipmentAlertSeveritySchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { loadEquipmentAlert, loadEquipmentAlertPage, mutateEquipmentAlert } from "@/services/equipment-alert-server-service";

const mutationSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("createRule"), ruleCode: z.string().trim().regex(/^[A-Za-z0-9_-]+$/).max(40),
    name: z.string().trim().min(1).max(120), connectionId: z.string().uuid(), pointId: z.string().uuid().nullable().optional(),
    ruleType: equipmentAlertRuleTypeSchema, thresholdValue: z.number().nullable().optional(), severity: equipmentAlertSeveritySchema,
    defaultAssignee: z.string().trim().min(1).max(80), reason: z.string().trim().min(4).max(500) }),
  z.object({ operation: z.literal("actOnRule"), id: z.string().uuid(), action: z.enum(["ACTIVATE", "PAUSE"]),
    reason: z.string().trim().min(4).max(500), expectedVersion: z.number().int().nonnegative() }),
  z.object({ operation: z.literal("actOnAlert"), id: z.string().uuid(),
    action: z.enum(["ACKNOWLEDGE", "START_PROCESSING", "RESOLVE", "CLOSE", "LINK_REPAIR"]),
    reason: z.string().trim().min(4).max(500), expectedVersion: z.number().int().nonnegative(),
    assignee: z.string().trim().max(80).nullable().optional(), resolutionNotes: z.string().trim().max(1000).nullable().optional(),
    workOrderId: z.string().uuid().nullable().optional() }),
]).superRefine((input, context) => {
  if (input.operation === "createRule") {
    const numeric = input.ruleType === "HIGH_LIMIT" || input.ruleType === "LOW_LIMIT";
    if (numeric && (!input.pointId || input.thresholdValue == null))
      context.addIssue({ code: "custom", message: "数值报警必须选择点位并填写阈值" });
    if (!numeric && (input.pointId || input.thresholdValue != null))
      context.addIssue({ code: "custom", message: "通讯失败报警不能填写点位或阈值" });
  }
  if (input.operation === "actOnAlert" && input.action === "RESOLVE"
      && (!input.resolutionNotes || input.resolutionNotes.length < 4))
    context.addIssue({ code: "custom", path: ["resolutionNotes"], message: "解决报警必须填写解决说明" });
  if (input.operation === "actOnAlert" && input.action === "LINK_REPAIR" && !input.workOrderId)
    context.addIssue({ code: "custom", path: ["workOrderId"], message: "关联维修必须选择维修工单" });
});

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
      if (!parsed.success) return Response.json({ message: "报警编号参数无效", requestId },
        { status: 400, headers: { "X-Request-Id": requestId } });
      const result = await loadEquipmentAlert(parsed.data, requestId);
      return Response.json({ alert: result.alert }, { headers: { "X-Request-Id": result.requestId } });
    }
    const result = await loadEquipmentAlertPage(requestId);
    if (result.source === "unavailable") return Response.json({ message: result.message, requestId: result.requestId },
      { status: result.status, headers: { "X-Request-Id": result.requestId } });
    return Response.json({ data: result }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备报警服务发生未预期错误"); }
}

export async function POST(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "设备报警参数无效，请检查规则、原因和版本", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateEquipmentAlert(parsed.data, requestId);
    return Response.json(result.alert ? { alert: result.alert } : { rule: result.rule },
      { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备报警服务发生未预期错误"); }
}
