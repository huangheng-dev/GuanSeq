import "server-only";

import { randomUUID } from "node:crypto";

import { equipmentAssetPageSchema, equipmentTelemetryActionResultSchema,
  equipmentTelemetryAutomationActionResultSchema, equipmentTelemetryCleanupResultSchema,
  equipmentTelemetryConnectionPageSchema, equipmentTelemetryConnectionSchema, equipmentTelemetryRetentionPolicySchema,
  equipmentTelemetrySamplePageSchema, type EquipmentAssetPage, type EquipmentTelemetryActionResult,
  type EquipmentTelemetryAutomationActionResult, type EquipmentTelemetryCleanupResult,
  type EquipmentTelemetryConnection, type EquipmentTelemetryConnectionPage, type EquipmentTelemetryEndpointType,
  type EquipmentTelemetryProtocol, type EquipmentTelemetryQuality, type EquipmentTelemetryRegisterType, type EquipmentTelemetryRetentionPolicy,
  type EquipmentTelemetrySamplePage, type EquipmentTelemetryValueType } from "@/lib/contracts";
import { GuanSeqApiError, readApiError, requestGuanSeqApi } from "@/services/guanseq-api-server";

export type EquipmentTelemetryPointInput = {
  pointCode: string; name: string; registerType: EquipmentTelemetryRegisterType; address: number;
  mqttTopic?: string | null; mqttValuePointer?: string | null;
  valueType: EquipmentTelemetryValueType; scale: number; valueOffset: number; engineeringUnit?: string | null;
  validMin?: number | null; validMax?: number | null; sortOrder: number;
};

export type EquipmentTelemetryMutation =
  | { operation: "create"; connectionCode: string; name: string; assetId: string; protocol: EquipmentTelemetryProtocol;
      endpointType: EquipmentTelemetryEndpointType;
      host: string; port: number; unitId: number; connectTimeoutMs: number; readTimeoutMs: number;
      mqtt?: { transport: "TCP" | "TLS"; clientId: string; qos: number; credentialReference?: string | null;
        messageIdPointer: string; deviceTimePointer?: string | null } | null;
      pollIntervalSeconds: number; points: EquipmentTelemetryPointInput[]; reason: string }
  | { operation: "test" | "activate" | "pause" | "poll"; id: string; reason: string; expectedVersion: number };

export type EquipmentTelemetryHistoryFilter = {
  connectionId: string; pointCode?: string; quality?: EquipmentTelemetryQuality; from?: string; to?: string;
  page?: number; size?: number;
};

export type EquipmentTelemetryLifecycleMutation =
  | { operation: "updatePolicy"; retentionDays: number; automaticCleanupEnabled: boolean;
      cleanupIntervalHours: number; expectedVersion: number; reason: string }
  | { operation: "cleanup" | "runNow"; expectedVersion: number; reason: string }
  | { operation: "acknowledge"; runId: string; note: string };

export type EquipmentTelemetryPageData =
  | { source: "backend"; page: EquipmentTelemetryConnectionPage; assets: EquipmentAssetPage; requestId: string }
  | { source: "unavailable"; page: null; assets: EquipmentAssetPage | null; status: number; message: string; requestId: string };

async function responseMessage(response: Response, fallback: string) {
  try { return ((await response.json()) as { message?: string }).message ?? fallback; }
  catch { return fallback; }
}

export async function loadEquipmentTelemetryPage(requestId: string): Promise<EquipmentTelemetryPageData> {
  const [connectionResponse, assetResponse] = await Promise.all([
    requestGuanSeqApi("/api/v1/equipment/telemetry-connections?page=0&size=100", requestId),
    requestGuanSeqApi("/api/v1/equipment/assets?page=0&size=200&status=ALL&category=ALL", `${requestId}-assets`),
  ]);
  const assets = assetResponse?.ok ? equipmentAssetPageSchema.parse(await assetResponse.json()) : null;
  if (!connectionResponse) return { source: "unavailable", page: null, assets, status: 503,
    message: "设备采集连接服务暂时不可用", requestId };
  const responseRequestId = connectionResponse.headers.get("X-Request-Id") ?? requestId;
  if (!connectionResponse.ok) return { source: "unavailable", page: null, assets, status: connectionResponse.status,
    message: await responseMessage(connectionResponse, connectionResponse.status === 403 ? "当前角色无权读取设备采集连接" : "设备采集连接加载失败"),
    requestId: responseRequestId };
  if (!assets) return { source: "unavailable", page: null, assets: null, status: assetResponse?.status ?? 503,
    message: "设备台账不可用，无法建立采集连接", requestId: responseRequestId };
  return { source: "backend", page: equipmentTelemetryConnectionPageSchema.parse(await connectionResponse.json()),
    assets, requestId: responseRequestId };
}

export async function getEquipmentTelemetryPageData(pathname: string): Promise<EquipmentTelemetryPageData | null> {
  if (pathname !== "/equipment/telemetry") return null;
  return loadEquipmentTelemetryPage(`web-equipment-telemetry-${randomUUID()}`);
}

export async function loadEquipmentTelemetryConnection(id: string, requestId: string): Promise<{
  connection: EquipmentTelemetryConnection; requestId: string;
}> {
  const response = await requestGuanSeqApi(`/api/v1/equipment/telemetry-connections/${id}`, requestId);
  if (!response) throw new GuanSeqApiError("设备采集连接服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "设备采集连接详情加载失败");
  return { connection: equipmentTelemetryConnectionSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function mutateEquipmentTelemetry(input: EquipmentTelemetryMutation, requestId: string): Promise<{
  connection: EquipmentTelemetryConnection; actionResult?: EquipmentTelemetryActionResult; requestId: string;
}> {
  const create = input.operation === "create";
  const path = create ? "/api/v1/equipment/telemetry-connections"
    : `/api/v1/equipment/telemetry-connections/${input.id}/${input.operation}`;
  const body = create ? { connectionCode: input.connectionCode, name: input.name, assetId: input.assetId,
    protocol: input.protocol, endpointType: input.endpointType, host: input.host, port: input.port, unitId: input.unitId,
    mqtt: input.mqtt ?? null,
    connectTimeoutMs: input.connectTimeoutMs, readTimeoutMs: input.readTimeoutMs,
    pollIntervalSeconds: input.pollIntervalSeconds, points: input.points, reason: input.reason }
    : { reason: input.reason, expectedVersion: input.expectedVersion };
  const response = await requestGuanSeqApi(path, requestId, { method: "POST", body: JSON.stringify(body) }, 15000);
  if (!response) throw new GuanSeqApiError("设备采集连接服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "设备采集连接操作失败");
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  const payload = await response.json();
  if (input.operation === "test" || input.operation === "poll") {
    const actionResult = equipmentTelemetryActionResultSchema.parse(payload);
    return { connection: actionResult.connection, actionResult, requestId: responseRequestId };
  }
  return { connection: equipmentTelemetryConnectionSchema.parse(payload), requestId: responseRequestId };
}

export async function loadEquipmentTelemetryHistory(filter: EquipmentTelemetryHistoryFilter,
  requestId: string): Promise<{ page: EquipmentTelemetrySamplePage; requestId: string }> {
  const params = new URLSearchParams({ connectionId: filter.connectionId,
    page: String(filter.page ?? 0), size: String(filter.size ?? 50) });
  if (filter.pointCode) params.set("pointCode", filter.pointCode);
  if (filter.quality) params.set("quality", filter.quality);
  if (filter.from) params.set("from", filter.from);
  if (filter.to) params.set("to", filter.to);
  const response = await requestGuanSeqApi(`/api/v1/equipment/telemetry-samples?${params}`, requestId);
  if (!response) throw new GuanSeqApiError("设备样本历史服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "设备样本历史加载失败");
  return { page: equipmentTelemetrySamplePageSchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function loadEquipmentTelemetryRetentionPolicy(requestId: string): Promise<{
  policy: EquipmentTelemetryRetentionPolicy; requestId: string;
}> {
  const response = await requestGuanSeqApi("/api/v1/equipment/telemetry-retention-policy", requestId);
  if (!response) throw new GuanSeqApiError("设备样本保留策略服务暂时不可用", 503);
  if (!response.ok) await readApiError(response, "设备样本保留策略加载失败");
  return { policy: equipmentTelemetryRetentionPolicySchema.parse(await response.json()),
    requestId: response.headers.get("X-Request-Id") ?? requestId };
}

export async function mutateEquipmentTelemetryLifecycle(input: EquipmentTelemetryLifecycleMutation,
  requestId: string): Promise<{ policy?: EquipmentTelemetryRetentionPolicy;
    cleanupResult?: EquipmentTelemetryCleanupResult; automationResult?: EquipmentTelemetryAutomationActionResult;
    requestId: string }> {
  const cleanup = input.operation === "cleanup";
  const runNow = input.operation === "runNow"; const acknowledge = input.operation === "acknowledge";
  const path = cleanup ? "/api/v1/equipment/telemetry-retention-policy/cleanup"
    : runNow ? "/api/v1/equipment/telemetry-retention-policy/automation/run-now"
    : acknowledge ? `/api/v1/equipment/telemetry-retention-runs/${input.runId}/acknowledge`
    : "/api/v1/equipment/telemetry-retention-policy";
  const body = acknowledge ? { note: input.note } : input.operation === "updatePolicy"
    ? { retentionDays: input.retentionDays, automaticCleanupEnabled: input.automaticCleanupEnabled,
        cleanupIntervalHours: input.cleanupIntervalHours, expectedVersion: input.expectedVersion, reason: input.reason }
    : { expectedVersion: input.expectedVersion, reason: input.reason };
  const response = await requestGuanSeqApi(path, requestId, {
      method: input.operation === "updatePolicy" ? "PUT" : "POST", body: JSON.stringify(body),
    });
  if (!response) throw new GuanSeqApiError("设备样本保留操作服务暂时不可用，未保存任何更改", 503);
  if (!response.ok) await readApiError(response, "设备样本保留操作失败");
  const responseRequestId = response.headers.get("X-Request-Id") ?? requestId;
  if (cleanup) return { cleanupResult: equipmentTelemetryCleanupResultSchema.parse(await response.json()),
    requestId: responseRequestId };
  if (runNow || acknowledge) return {
    automationResult: equipmentTelemetryAutomationActionResultSchema.parse(await response.json()),
    requestId: responseRequestId,
  };
  return { policy: equipmentTelemetryRetentionPolicySchema.parse(await response.json()),
    requestId: responseRequestId };
}
