import { afterEach, describe, expect, it, vi } from "vitest";

import { equipmentAlertPageSchema, equipmentAlertSchema, type EquipmentAlert, type EquipmentAlertRule } from "@/lib/contracts";
import { EquipmentAlertClientError, loadEquipmentAlertDetail, refreshEquipmentAlertPage,
  submitEquipmentAlertMutation } from "./equipment-alert-client-service";

const rule: EquipmentAlertRule = {
  id: "e1000000-0000-4000-8000-000000000001", ruleCode: "LOAD_HIGH", name: "主轴负载过高",
  connectionId: "f1000000-0000-4000-8000-000000000001", connectionCode: "TEL-MODBUS-001", connectionName: "加工中心连接",
  assetId: "a1000000-0000-4000-8000-000000000001", assetCode: "EQ-CNC-001", assetName: "一号加工中心",
  pointId: "f2000000-0000-4000-8000-000000000001", pointCode: "SPINDLE_LOAD", pointName: "主轴负载",
  ruleType: "HIGH_LIMIT", thresholdValue: 80, severity: "WARNING", defaultAssignee: "设备主管", status: "ACTIVE",
  version: 0, createdAt: "2026-08-26T08:00:00Z", updatedAt: "2026-08-26T08:00:00Z", availableActions: ["PAUSE"], events: [],
};
const alert: EquipmentAlert = {
  id: "e2000000-0000-4000-8000-000000000001", alertNumber: "ALM-20260826-160000-ABCD1234",
  ruleId: rule.id, ruleCode: rule.ruleCode, ruleName: rule.name, assetId: rule.assetId, assetCode: rule.assetCode,
  assetName: rule.assetName, connectionId: rule.connectionId, connectionCode: rule.connectionCode,
  pointId: rule.pointId, pointCode: rule.pointCode, pointName: rule.pointName, ruleType: rule.ruleType,
  severity: rule.severity, status: "OPEN", conditionActive: true, observedValue: 86.5, observedQuality: "GOOD",
  failureCode: null, assignee: "设备主管", resolutionNotes: null, linkedWorkOrderId: null, linkedWorkOrderNumber: null,
  version: 0, firstOccurredAt: "2026-08-26T08:10:00Z", lastOccurredAt: "2026-08-26T08:10:00Z",
  recoveredAt: null, acknowledgedAt: null, processingStartedAt: null, resolvedAt: null, closedAt: null,
  updatedAt: "2026-08-26T08:10:00Z", availableActions: ["ACKNOWLEDGE", "LINK_REPAIR"], events: [{
    id: "e3000000-0000-4000-8000-000000000001", actorUserId: null, action: "OCCURRED", fromStatus: null,
    toStatus: "OPEN", reason: "采集值达到或超过上限阈值", requestId: "SAMPLE-001",
    details: { observedValue: 86.5, conditionActive: true }, occurredAt: "2026-08-26T08:10:00Z",
  }],
};
const alertPage = { items: [alert], totalElements: 1, page: 0, size: 100, totalPages: 1,
  activeConditionCount: 1, unclosedCount: 1, canManage: true };
const rulePage = { items: [rule], totalElements: 1, page: 0, size: 100, totalPages: 1, canManage: true };
const emptyConnectionPage = { items: [], totalElements: 0, page: 0, size: 100, totalPages: 0, canManage: true };
const emptyWorkOrderPage = { items: [], totalElements: 0, page: 0, size: 200, totalPages: 0, canMaintain: true };

afterEach(() => vi.unstubAllGlobals());

describe("equipment alert client service", () => {
  it("parses dashboard and preserves condition responsibility separation", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ data: { alertPage, rulePage,
      connections: emptyConnectionPage, workOrders: emptyWorkOrderPage, requestId: "alert-page-001" } })));
    const result = await refreshEquipmentAlertPage();
    expect(result.alertPage.items[0]).toEqual(expect.objectContaining({ conditionActive: true, status: "OPEN" }));
    expect(equipmentAlertPageSchema.parse(alertPage).unclosedCount).toBe(1);
  });

  it("loads detail and accepts nullable system actors", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ alert })));
    await expect(loadEquipmentAlertDetail(alert.id)).resolves.toEqual(alert);
    expect(equipmentAlertSchema.parse(alert).events[0].actorUserId).toBeNull();
  });

  it("sends explicit threshold rule and backend version actions", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(Response.json({ rule })).mockResolvedValueOnce(Response.json({ alert: {
      ...alert, status: "ACKNOWLEDGED", version: 1, acknowledgedAt: "2026-08-26T08:20:00Z",
      availableActions: ["START_PROCESSING", "LINK_REPAIR"] } }));
    vi.stubGlobal("fetch", fetchMock);
    await submitEquipmentAlertMutation({ operation: "createRule", ruleCode: "LOAD_HIGH", name: "主轴负载过高",
      connectionId: rule.connectionId, pointId: rule.pointId, ruleType: "HIGH_LIMIT", thresholdValue: 80,
      severity: "WARNING", defaultAssignee: "设备主管", reason: "建立主轴负载报警责任" });
    await submitEquipmentAlertMutation({ operation: "actOnAlert", id: alert.id, action: "ACKNOWLEDGE",
      reason: "确认报警责任", expectedVersion: 0, assignee: "设备主管" });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(expect.objectContaining({
      operation: "createRule", ruleType: "HIGH_LIMIT", thresholdValue: 80, pointId: rule.pointId,
    }));
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(expect.objectContaining({
      operation: "actOnAlert", action: "ACKNOWLEDGE", expectedVersion: 0,
    }));
  });

  it("surfaces conflict request evidence", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ message: "报警已被其他用户修改",
      requestId: "alert-conflict-001" }, { status: 409 })));
    await expect(submitEquipmentAlertMutation({ operation: "actOnAlert", id: alert.id, action: "ACKNOWLEDGE",
      reason: "确认报警责任", expectedVersion: 0 })).rejects.toEqual(
      expect.objectContaining<Partial<EquipmentAlertClientError>>({ status: 409, requestId: "alert-conflict-001" }));
  });
});
