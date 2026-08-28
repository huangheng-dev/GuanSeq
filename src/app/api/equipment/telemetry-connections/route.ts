import { z } from "zod";

import { equipmentTelemetryEndpointTypeSchema, equipmentTelemetryProtocolSchema, equipmentTelemetryRegisterTypeSchema,
  equipmentTelemetryValueTypeSchema } from "@/lib/contracts";
import { GuanSeqApiError } from "@/services/guanseq-api-server";
import { loadEquipmentTelemetryConnection, loadEquipmentTelemetryPage,
  mutateEquipmentTelemetry } from "@/services/equipment-telemetry-server-service";

const pointSchema = z.object({
  pointCode: z.string().trim().regex(/^[A-Z0-9][A-Z0-9_-]{1,59}$/), name: z.string().trim().min(1).max(120),
  registerType: equipmentTelemetryRegisterTypeSchema, address: z.number().int().min(0).max(65535),
  mqttTopic: z.string().trim().min(1).max(512).nullable().optional(),
  mqttValuePointer: z.string().trim().regex(/^\/.*/).max(253).nullable().optional(),
  valueType: equipmentTelemetryValueTypeSchema, scale: z.number().min(-1_000_000_000).max(1_000_000_000),
  valueOffset: z.number().min(-1_000_000_000).max(1_000_000_000), engineeringUnit: z.string().trim().max(24).nullable().optional(),
  validMin: z.number().nullable().optional(), validMax: z.number().nullable().optional(), sortOrder: z.number().int().min(1).max(1000),
});
const mutationSchema = z.discriminatedUnion("operation", [
  z.object({ operation: z.literal("create"), connectionCode: z.string().trim().regex(/^[A-Z0-9][A-Z0-9_-]{1,39}$/),
    name: z.string().trim().min(1).max(120), assetId: z.string().uuid(), protocol: equipmentTelemetryProtocolSchema,
    endpointType: equipmentTelemetryEndpointTypeSchema,
    host: z.string().trim().min(1).max(253).regex(/^[A-Za-z0-9._:-]+$/), port: z.number().int().min(1).max(65535),
    unitId: z.number().int().min(0).max(247), connectTimeoutMs: z.number().int().min(100).max(10000),
    mqtt: z.object({ transport: z.enum(["TCP", "TLS"]), clientId: z.string().trim().regex(/^[A-Za-z0-9_-]+$/).max(128),
      qos: z.number().int().min(0).max(1), credentialReference: z.string().trim().regex(/^[A-Za-z0-9_-]+$/).max(80).nullable().optional(),
      messageIdPointer: z.string().trim().regex(/^\/.*/).max(253),
      deviceTimePointer: z.string().trim().regex(/^\/.*/).max(253).nullable().optional() }).nullable().optional(),
    readTimeoutMs: z.number().int().min(100).max(10000), pollIntervalSeconds: z.number().int().min(1).max(3600),
    points: z.array(pointSchema).min(1).max(100), reason: z.string().trim().min(4).max(500) }),
  ...(["test", "activate", "pause", "poll"] as const).map((operation) => z.object({ operation: z.literal(operation),
    id: z.string().uuid(), reason: z.string().trim().min(4).max(500), expectedVersion: z.number().int().nonnegative() })),
]).superRefine((input, context) => {
  if (input.operation !== "create") return;
  const mqtt = input.protocol === "MQTT_3_1_1";
  if (mqtt && !input.mqtt) context.addIssue({ code: "custom", path: ["mqtt"], message: "MQTT 配置不能为空" });
  if (!mqtt && input.mqtt) context.addIssue({ code: "custom", path: ["mqtt"], message: "Modbus 连接不能携带 MQTT 配置" });
  if (mqtt && !["SIMULATOR", "EXTERNAL_BROKER"].includes(input.endpointType))
    context.addIssue({ code: "custom", path: ["endpointType"], message: "MQTT 仅支持仿真或外部 Broker" });
  if (!mqtt && input.endpointType === "EXTERNAL_BROKER")
    context.addIssue({ code: "custom", path: ["endpointType"], message: "Modbus 不支持 Broker 端点" });
  input.points.forEach((point, index) => {
    if (mqtt && (point.registerType !== "MQTT_JSON" || !point.mqttTopic || !point.mqttValuePointer))
      context.addIssue({ code: "custom", path: ["points", index], message: "MQTT 点位必须配置精确 Topic 和 JSON Pointer" });
    if (!mqtt && (point.registerType === "MQTT_JSON" || point.mqttTopic || point.mqttValuePointer))
      context.addIssue({ code: "custom", path: ["points", index], message: "Modbus 点位不能携带 MQTT 字段" });
  });
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
      const parsedId = z.string().uuid().safeParse(id);
      if (!parsedId.success) return Response.json({ message: "设备采集连接编号参数无效", requestId },
        { status: 400, headers: { "X-Request-Id": requestId } });
      const detail = await loadEquipmentTelemetryConnection(parsedId.data, requestId);
      return Response.json({ connection: detail.connection }, { headers: { "X-Request-Id": detail.requestId } });
    }
    const result = await loadEquipmentTelemetryPage(requestId);
    if (result.source === "unavailable") return Response.json({ message: result.message, requestId: result.requestId },
      { status: result.status, headers: { "X-Request-Id": result.requestId } });
    return Response.json({ page: result.page }, { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备采集连接服务发生未预期错误"); }
}

export async function POST(request: Request) {
  const requestId = requestIdFrom(request);
  const parsed = mutationSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) return Response.json({ message: "设备采集参数无效，请检查端点、点位、原因和版本", requestId },
    { status: 400, headers: { "X-Request-Id": requestId } });
  try {
    const result = await mutateEquipmentTelemetry(parsed.data, requestId);
    return Response.json(result.actionResult ? { actionResult: result.actionResult } : { connection: result.connection },
      { headers: { "X-Request-Id": result.requestId } });
  } catch (error) { return errorResponse(error, requestId, "设备采集连接服务发生未预期错误"); }
}
