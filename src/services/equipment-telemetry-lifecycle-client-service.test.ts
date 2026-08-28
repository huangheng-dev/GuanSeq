import { afterEach, describe, expect, it, vi } from "vitest";

import { EquipmentTelemetryClientError } from "./equipment-telemetry-client-service";
import { loadEquipmentTelemetryRetention, loadEquipmentTelemetrySampleHistory,
  submitEquipmentTelemetryLifecycleMutation } from "./equipment-telemetry-lifecycle-client-service";

const policy = {
  id: null, retentionDays: 30, expiredSampleCount: 0, cutoffAt: "2026-07-27T04:00:00Z",
  version: 0, defaultPolicy: true, canManage: true, schedulerAvailable: true,
  automaticCleanupEnabled: false, cleanupIntervalHours: 24, nextCleanupAt: null,
  lastAutomationStatus: null, lastAutomationCompletedAt: null, consecutiveFailures: 0,
  updatedBy: null, updatedAt: null, events: [], automationRuns: [],
};
const page = {
  items: [{ id: "f3000000-0000-4000-8000-000000000001",
    pointId: "f2000000-0000-4000-8000-000000000001", pointCode: "SPINDLE_LOAD", rawValue: "685",
    numericValue: 68.5, booleanValue: null, quality: "GOOD", deviceTime: null,
    receivedAt: "2026-08-26T04:00:00Z", sequenceNumber: 1, messageVersion: 1, sourceProtocol: "MODBUS_TCP" }],
  totalElements: 1, page: 0, size: 50, totalPages: 1,
  connectionId: "f1000000-0000-4000-8000-000000000001",
  windowFrom: "2026-08-25T04:00:00Z", windowTo: "2026-08-26T04:00:00Z",
};

afterEach(() => vi.unstubAllGlobals());

describe("equipment telemetry lifecycle client service", () => {
  it("loads finite history filters and the default retention policy", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(Response.json({ page }))
      .mockResolvedValueOnce(Response.json({ policy }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(loadEquipmentTelemetrySampleHistory({ connectionId: page.connectionId,
      pointCode: "SPINDLE_LOAD", quality: "GOOD" })).resolves.toEqual(page);
    expect(fetchMock.mock.calls[0][0]).toContain("pointCode=SPINDLE_LOAD");
    expect(fetchMock.mock.calls[0][0]).toContain("quality=GOOD");
    await expect(loadEquipmentTelemetryRetention()).resolves.toEqual(policy);
  });

  it("submits versioned policy, cleanup, run and acknowledgement commands", async () => {
    const updated = { ...policy, id: "f4000000-0000-4000-8000-000000000001", retentionDays: 14,
      defaultPolicy: false, updatedBy: "20000000-0000-4000-8000-000000000001", updatedAt: "2026-08-26T04:10:00Z" };
    const cleanup = { policy: updated, deletedSampleCount: 12, cutoffAt: "2026-08-12T04:10:00Z",
      requestId: "cleanup-001", occurredAt: "2026-08-26T04:10:00Z", replayed: false };
    const run = { id: "f5000000-0000-4000-8000-000000000001", triggerType: "USER_RETRY", status: "SUCCEEDED",
      initiatedBy: "20000000-0000-4000-8000-000000000001", instanceId: "guanseq-test", requestId: "run-001",
      reason: "立即验证自动清理", cutoffAt: "2026-08-12T04:10:00Z", deletedSampleCount: 0,
      remainingExpiredCount: 0, failureCode: null, failureSummary: null, attentionStatus: "NONE",
      responsibleRoles: [], acknowledgedBy: null, acknowledgedAt: null, acknowledgementNote: null,
      startedAt: "2026-08-26T04:10:00Z", completedAt: "2026-08-26T04:10:01Z" };
    const automationResult = { policy: { ...updated, automationRuns: [run] }, run, replayed: false };
    const fetchMock = vi.fn().mockResolvedValueOnce(Response.json({ policy: updated }))
      .mockResolvedValueOnce(Response.json({ cleanupResult: cleanup }))
      .mockResolvedValueOnce(Response.json({ automationResult }))
      .mockResolvedValueOnce(Response.json({ automationResult }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(submitEquipmentTelemetryLifecycleMutation({ operation: "updatePolicy", retentionDays: 14,
      automaticCleanupEnabled: true, cleanupIntervalHours: 12,
      expectedVersion: 0, reason: "调整试点保留周期" })).resolves.toEqual(updated);
    await expect(submitEquipmentTelemetryLifecycleMutation({ operation: "cleanup", expectedVersion: 0,
      reason: "清理超过保留期样本" })).resolves.toEqual(cleanup);
    await expect(submitEquipmentTelemetryLifecycleMutation({ operation: "runNow", expectedVersion: 0,
      reason: "立即验证自动清理" })).resolves.toEqual(automationResult);
    await expect(submitEquipmentTelemetryLifecycleMutation({ operation: "acknowledge", runId: run.id,
      note: "设备经理确认并跟踪恢复" })).resolves.toEqual(automationResult);
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(expect.objectContaining({ operation: "cleanup",
      expectedVersion: 0 }));
    expect(JSON.parse(fetchMock.mock.calls[3][1].body)).toEqual({ operation: "acknowledge", runId: run.id,
      note: "设备经理确认并跟踪恢复" });
  });

  it("preserves backend conflict request evidence", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ message: "保留策略已被其他用户修改",
      requestId: "retention-conflict" }, { status: 409 })));
    await expect(submitEquipmentTelemetryLifecycleMutation({ operation: "cleanup", expectedVersion: 3,
      reason: "尝试清理过期样本" })).rejects.toEqual(
      expect.objectContaining<Partial<EquipmentTelemetryClientError>>({ status: 409, requestId: "retention-conflict" }));
  });
});
