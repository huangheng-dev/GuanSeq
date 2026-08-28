import { afterEach, describe, expect, it, vi } from "vitest";

import { equipmentTelemetryFieldAcceptanceContextSchema,
  type EquipmentTelemetryFieldAcceptanceContext } from "@/lib/contracts";
import { EquipmentTelemetryFieldAcceptanceClientError, loadEquipmentTelemetryFieldAcceptanceContext,
  submitEquipmentTelemetryFieldAcceptanceMutation } from "./equipment-telemetry-field-acceptance-client-service";

const context: EquipmentTelemetryFieldAcceptanceContext = {
  connectionId: "aa000000-0000-4000-8000-000000000001", connectionCode: "TEL-PHY-001",
  connectionName: "一号加工中心现场连接", protocol: "MODBUS_TCP", endpointType: "PHYSICAL_DEVICE",
  fieldEligible: true, latestTechnicalPrecheckPassed: true, fieldAccepted: false,
  canMaintain: true, canApprove: true,
  acceptance: {
    id: "ab000000-0000-4000-8000-000000000001", acceptanceNumber: "TFA-20260826190000-ABC123",
    connectionId: "aa000000-0000-4000-8000-000000000001", status: "DRAFT",
    networkApproved: true, securityValidated: true, readOnlyConfirmed: true,
    disconnectRecoveryVerified: true, capacityVerified: true, pointMappingApproved: true,
    responsibleOwner: "现场设备负责人", testWindowStart: "2026-08-26T09:00:00Z",
    testWindowEnd: "2026-08-26T11:00:00Z", evidenceReference: "SITE-REPORT-001",
    notes: "现场证据已经归档", rejectionReason: null, version: 1,
    createdBy: "20000000-0000-4000-8000-000000000001", createdAt: "2026-08-26T08:00:00Z",
    submittedBy: null, submittedAt: null, approvedBy: null, approvedAt: null,
    rejectedBy: null, rejectedAt: null, updatedAt: "2026-08-26T08:30:00Z",
    availableActions: ["UPDATE", "SUBMIT"],
    events: [{ id: "ac000000-0000-4000-8000-000000000001",
      actorUserId: "20000000-0000-4000-8000-000000000001", action: "UPDATED",
      fromStatus: "DRAFT", toStatus: "DRAFT", reason: "补齐现场证据", requestId: "field-update-001",
      details: { completedCheckCount: 6 }, occurredAt: "2026-08-26T08:30:00Z" }],
  },
};

afterEach(() => vi.unstubAllGlobals());

describe("equipment telemetry field acceptance client service", () => {
  it("parses independent technical precheck and field acceptance facts", () => {
    expect(equipmentTelemetryFieldAcceptanceContextSchema.parse(context)).toEqual(expect.objectContaining({
      latestTechnicalPrecheckPassed: true, fieldAccepted: false,
    }));
    expect(context.acceptance?.events[0].details.completedCheckCount).toBe(6);
  });

  it("loads a connection-scoped acceptance context", async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(Response.json({ context })));
    vi.stubGlobal("fetch", fetchMock);
    await expect(loadEquipmentTelemetryFieldAcceptanceContext(context.connectionId)).resolves.toEqual(context);
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining(encodeURIComponent(context.connectionId)),
      { cache: "no-store" });
  });

  it("sends draft evidence and optimistic approval actions", async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(Response.json({ context })));
    vi.stubGlobal("fetch", fetchMock);
    await submitEquipmentTelemetryFieldAcceptanceMutation({ operation: "save", connectionId: context.connectionId,
      networkApproved: true, securityValidated: true, readOnlyConfirmed: true,
      disconnectRecoveryVerified: true, capacityVerified: true, pointMappingApproved: true,
      responsibleOwner: "现场设备负责人", testWindowStart: "2026-08-26T09:00:00Z",
      testWindowEnd: "2026-08-26T11:00:00Z", evidenceReference: "SITE-REPORT-001",
      notes: "证据归档", expectedVersion: 1, reason: "补齐六项现场证据" });
    await submitEquipmentTelemetryFieldAcceptanceMutation({ operation: "act", connectionId: context.connectionId,
      action: "SUBMIT", expectedVersion: 1, reason: "提交现场验收证据审核" });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(expect.objectContaining({
      operation: "save", capacityVerified: true, expectedVersion: 1,
    }));
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(expect.objectContaining({
      action: "SUBMIT", expectedVersion: 1,
    }));
  });

  it("surfaces backend conflicts with request evidence", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ message: "现场验收单已被其他用户修改",
      requestId: "field-conflict-001" }, { status: 409 })));
    await expect(submitEquipmentTelemetryFieldAcceptanceMutation({ operation: "act",
      connectionId: context.connectionId, action: "SUBMIT", expectedVersion: 0,
      reason: "提交现场验收证据审核" })).rejects.toEqual(
      expect.objectContaining<Partial<EquipmentTelemetryFieldAcceptanceClientError>>({
        status: 409, requestId: "field-conflict-001",
      }));
  });
});
