import { z } from "zod";

import { equipmentTelemetryFieldAcceptanceActionSchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { loadEquipmentTelemetryFieldAcceptance, mutateEquipmentTelemetryFieldAcceptance
} from "@/services/equipment-telemetry-field-acceptance-server-service";

const saveSchema = z.object({ operation: z.literal("save"), connectionId: z.string().uuid(),
  networkApproved: z.boolean(), securityValidated: z.boolean(), readOnlyConfirmed: z.boolean(),
  disconnectRecoveryVerified: z.boolean(), capacityVerified: z.boolean(), pointMappingApproved: z.boolean(),
  responsibleOwner: z.string().trim().max(80).nullable().optional(),
  testWindowStart: z.string().datetime().nullable().optional(), testWindowEnd: z.string().datetime().nullable().optional(),
  evidenceReference: z.string().trim().max(240).nullable().optional(), notes: z.string().trim().max(1000).nullable().optional(),
  expectedVersion: z.number().int().nonnegative().nullable().optional(), reason: z.string().trim().min(4).max(500),
}).superRefine((input, context) => {
  if (Boolean(input.testWindowStart) !== Boolean(input.testWindowEnd))
    context.addIssue({ code: "custom", path: ["testWindowEnd"], message: "测试窗口必须同时填写" });
});
const actSchema = z.object({ operation: z.literal("act"), connectionId: z.string().uuid(),
  action: equipmentTelemetryFieldAcceptanceActionSchema.exclude(["UPDATE"]),
  expectedVersion: z.number().int().nonnegative(), reason: z.string().trim().min(4).max(500) });
const mutationSchema = z.union([saveSchema, actSchema]);

function requestIdFrom(request: Request) { return request.headers.get("X-Request-Id") ?? crypto.randomUUID(); }
function errorResponse(error: unknown, requestId: string, fallback: string) {
  const status = error instanceof GuanSeqApiError ? error.status : 500;
  return Response.json({ message: error instanceof Error ? error.message : fallback, requestId },
    { status, headers: { "X-Request-Id": requestId } });
}

export async function GET(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = z.string().uuid().safeParse(new URL(request.url).searchParams.get("connectionId"));
  if (!parsed.success) return Response.json({ message: "采集连接编号无效", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await loadEquipmentTelemetryFieldAcceptance(parsed.data, requestId);
    return Response.json({ context: result.context }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "现场接入验收服务发生未预期错误"); }
}

export async function POST(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "现场验收参数无效，请检查证据、测试窗口、原因和版本", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateEquipmentTelemetryFieldAcceptance(parsed.data, requestId);
    return Response.json({ context: result.context }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "现场接入验收服务发生未预期错误"); }
}
