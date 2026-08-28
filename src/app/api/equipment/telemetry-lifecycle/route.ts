import { z } from "zod";

import { equipmentTelemetryQualitySchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { loadEquipmentTelemetryHistory, loadEquipmentTelemetryRetentionPolicy,
  mutateEquipmentTelemetryLifecycle } from "@/services/equipment-telemetry-server-service";

const mutationSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("updatePolicy"), retentionDays: z.number().int().min(7).max(3650),
    automaticCleanupEnabled: z.boolean(), cleanupIntervalHours: z.number().int().min(1).max(720),
    expectedVersion: z.number().int().nonnegative(), reason: z.string().trim().min(4).max(500) }),
  z.object({ operation: z.literal("cleanup"), expectedVersion: z.number().int().nonnegative(),
    reason: z.string().trim().min(4).max(500) }),
  z.object({ operation: z.literal("runNow"), expectedVersion: z.number().int().nonnegative(),
    reason: z.string().trim().min(4).max(500) }),
  z.object({ operation: z.literal("acknowledge"), runId: z.string().uuid(),
    note: z.string().trim().min(4).max(500) }),
]);

function requestIdFrom(request: Request) { return request.headers.get("X-Request-Id") ?? crypto.randomUUID(); }
function errorResponse(error: unknown, requestId: string, fallback: string) {
  const status = error instanceof GuanSeqApiError ? error.status : 500;
  return Response.json({ message: error instanceof Error ? error.message : fallback, requestId },
    { status, headers: { "X-Request-Id": requestId } });
}

export async function GET(request: Request) {
  const requestId = requestIdFrom(request); const params = new URL(request.url).searchParams;
  try {
    if (params.get("resource") === "policy") {
      const result = await loadEquipmentTelemetryRetentionPolicy(requestId);
      return Response.json({ policy: result.policy }, { headers: { "X-Request-Id": result.requestId } });
    }
    const parsed = z.object({ connectionId: z.string().uuid(), pointCode: z.string().trim().max(60).optional(),
      quality: equipmentTelemetryQualitySchema.optional() }).safeParse({ connectionId: params.get("connectionId"),
      pointCode: params.get("pointCode") || undefined, quality: params.get("quality") || undefined });
    if (!parsed.success) return Response.json({ message: "设备样本历史筛选参数无效", requestId },
      { status: 400, headers: { "X-Request-Id": requestId } });
    const result = await loadEquipmentTelemetryHistory(parsed.data, requestId);
    return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备样本生命周期服务发生未预期错误"); }
}

export async function POST(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "设备样本保留参数无效，请检查天数、原因和版本", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateEquipmentTelemetryLifecycle(parsed.data, requestId);
    return Response.json(result.cleanupResult ? { cleanupResult: result.cleanupResult }
      : result.automationResult ? { automationResult: result.automationResult } : { policy: result.policy },
      { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备样本生命周期服务发生未预期错误"); }
}
