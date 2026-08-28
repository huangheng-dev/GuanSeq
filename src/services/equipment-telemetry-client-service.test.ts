import { afterEach, describe, expect, it, vi } from "vitest";

import { equipmentTelemetryConnectionPageSchema, equipmentTelemetryConnectionSchema,
  type EquipmentTelemetryConnection } from "@/lib/contracts";
import { EquipmentTelemetryClientError, loadEquipmentTelemetryConnectionDetail,
  refreshEquipmentTelemetryConnections, submitEquipmentTelemetryMutation } from "./equipment-telemetry-client-service";

const connection: EquipmentTelemetryConnection = {
  id: "f1000000-0000-4000-8000-000000000001", connectionCode: "TEL-MODBUS-001", name: "加工中心只读连接",
  assetId: "a1000000-0000-4000-8000-000000000001", assetCode: "EQ-CNC-001", assetName: "一号精密加工中心",
  protocol: "MODBUS_TCP", endpointType: "SIMULATOR", host: "127.0.0.1", port: 1502, unitId: 1,
  mqtt: null, connectTimeoutMs: 1000, readTimeoutMs: 1000, pollIntervalSeconds: 5, status: "DRAFT",
  communicationStatus: "UNKNOWN", lastTestedAt: null, lastTestSucceededAt: null, lastAttemptAt: null,
  lastSuccessAt: null, lastErrorCode: null, lastErrorMessage: null, version: 0, canManage: true,
  points: [{ id: "f2000000-0000-4000-8000-000000000001", pointCode: "RUN_STATE", name: "运行状态",
    registerType: "HOLDING_REGISTER", address: 0, valueType: "UINT16", scale: 1, valueOffset: 0,
    mqttTopic: null, mqttValuePointer: null, engineeringUnit: "状态", validMin: 0, validMax: 4, sortOrder: 1 }], currentValues: [], events: [],
  createdAt: "2026-08-26T02:00:00Z", updatedAt: "2026-08-26T02:00:00Z",
};
const page = { items: [connection], totalElements: 1, page: 0, size: 100, totalPages: 1, canManage: true };

afterEach(() => vi.unstubAllGlobals());

describe("equipment telemetry client service", () => {
  it("parses connection page and detail", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ page }))
      .mockResolvedValueOnce(Response.json({ connection })));
    await expect(refreshEquipmentTelemetryConnections()).resolves.toEqual(page);
    await expect(loadEquipmentTelemetryConnectionDetail(connection.id)).resolves.toEqual(connection);
  });

  it("requires the OpenAPI totalElements page field", () => {
    expect(() => equipmentTelemetryConnectionPageSchema.parse({
      items: [], total: 0, page: 0, size: 20, totalPages: 0, canManage: true,
    })).toThrow();
    expect(equipmentTelemetryConnectionPageSchema.parse({
      items: [], totalElements: 0, page: 0, size: 20, totalPages: 0, canManage: true,
    }).totalElements).toBe(0);
  });

  it("parses technical preflight evidence without treating it as field acceptance", () => {
    const verified = equipmentTelemetryConnectionSchema.parse({ ...connection, events: [{
      id: "f3000000-0000-4000-8000-000000000001", actorUserId: "20000000-0000-4000-8000-000000000001",
      action: "TEST_SUCCEEDED", fromStatus: "DRAFT", toStatus: "DRAFT", reason: "执行仿真技术预检",
      requestId: "telemetry-preflight-001", occurredAt: "2026-08-26T02:05:00Z", details: {},
      verification: { verificationVersion: 1, evidenceLevel: "SIMULATION_TECHNICAL", technicalPassed: true,
        fieldAccepted: false, protocol: "MODBUS_TCP", endpointType: "SIMULATOR", pointCount: 1,
        returnedPointCount: 1, warningCount: 0,
        checks: [{ code: "POINT_COVERAGE", status: "PASSED", message: "已返回全部 1 个配置点位" }],
        pendingFieldChecks: ["现场网络与最小权限审批"], errorCode: null, errorMessage: null },
    }] });
    expect(verified.events[0].verification).toEqual(expect.objectContaining({
      evidenceLevel: "SIMULATION_TECHNICAL", technicalPassed: true, fieldAccepted: false,
    }));
  });

  it("sends configurable endpoint and points instead of mock values", async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ connection }, { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);
    await submitEquipmentTelemetryMutation({ operation: "create", connectionCode: connection.connectionCode,
      name: connection.name, assetId: connection.assetId, protocol: "MODBUS_TCP", endpointType: "SIMULATOR", host: "127.0.0.1", port: 1502,
      unitId: 1, connectTimeoutMs: 1000, readTimeoutMs: 1000, pollIntervalSeconds: 5,
      points: [{ pointCode: "RUN_STATE", name: "运行状态", registerType: "HOLDING_REGISTER", address: 0,
        valueType: "UINT16", scale: 1, valueOffset: 0, engineeringUnit: "状态", validMin: 0, validMax: 4, sortOrder: 1 }],
      reason: "建立标准协议仿真验证连接" });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(expect.objectContaining({ operation: "create",
      endpointType: "SIMULATOR", host: "127.0.0.1", points: [expect.objectContaining({ address: 0, valueType: "UINT16" })] }));
  });

  it("sends an external MQTT Broker mapping without credentials", async () => {
    const mqttConnection = { ...connection, connectionCode: "TEL-MQTT-001", protocol: "MQTT_3_1_1" as const,
      endpointType: "EXTERNAL_BROKER" as const, port: 1883,
      mqtt: { transport: "TCP" as const, clientId: "guanseq_site_a", qos: 1, credentialReference: "site_a",
        credentialConfigured: true, messageIdPointer: "/messageId", deviceTimePointer: "/deviceTime" } };
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ connection: mqttConnection }, { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);
    await submitEquipmentTelemetryMutation({ operation: "create", connectionCode: "TEL-MQTT-001",
      name: "外部 Broker 连接", assetId: connection.assetId, protocol: "MQTT_3_1_1", endpointType: "EXTERNAL_BROKER",
      host: "broker.factory.local", port: 1883, unitId: 0, connectTimeoutMs: 1000, readTimeoutMs: 1000,
      mqtt: { transport: "TCP", clientId: "guanseq_site_a", qos: 1, credentialReference: "site_a",
        messageIdPointer: "/messageId", deviceTimePointer: "/deviceTime" }, pollIntervalSeconds: 5,
      points: [{ pointCode: "SPINDLE_LOAD", name: "主轴负载", registerType: "MQTT_JSON", address: 0,
        mqttTopic: "factory/cnc/telemetry", mqttValuePointer: "/values/spindleLoad", valueType: "DECIMAL",
        scale: 1, valueOffset: 0, engineeringUnit: "%", validMin: 0, validMax: 120, sortOrder: 1 }],
      reason: "接入用户自有 Broker" });
    const body = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(body).toEqual(expect.objectContaining({ operation: "create", protocol: "MQTT_3_1_1",
      endpointType: "EXTERNAL_BROKER", mqtt: expect.objectContaining({ credentialReference: "site_a" }) }));
    expect(JSON.stringify(body)).not.toContain("password");
  });

  it("preserves protocol failure and backend conflict evidence", async () => {
    const failed = { success: false, message: "无法建立设备连接", connection: { ...connection,
      communicationStatus: "OFFLINE" as const, lastTestedAt: "2026-08-26T02:10:00Z",
      lastAttemptAt: "2026-08-26T02:10:00Z", lastErrorCode: "CONNECTION_REFUSED", lastErrorMessage: "无法建立设备连接" } };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ actionResult: failed })).mockResolvedValueOnce(
      Response.json({ message: "采集连接已被其他用户修改", requestId: "telemetry-conflict" }, { status: 409 })));
    await expect(submitEquipmentTelemetryMutation({ operation: "test", id: connection.id,
      reason: "验证断连状态", expectedVersion: 0 })).resolves.toEqual(failed);
    await expect(submitEquipmentTelemetryMutation({ operation: "activate", id: connection.id,
      reason: "尝试启用连接", expectedVersion: 0 })).rejects.toEqual(
      expect.objectContaining<Partial<EquipmentTelemetryClientError>>({ status: 409, requestId: "telemetry-conflict" }));
  });
});
