import { afterEach, describe, expect, it, vi } from "vitest";

import { equipmentOeePageSchema, equipmentOeeRecordSchema, type EquipmentOeeRecord } from "@/lib/contracts";
import { EquipmentOeeClientError, loadEquipmentOeeDetail, refreshEquipmentOeePage,
  submitEquipmentOeeMutation } from "./equipment-oee-client-service";

const record: EquipmentOeeRecord = {
  id: "de000000-0000-4000-8000-000000000001", recordNumber: "OEE-20260826160000-ABC123",
  assetId: "a1000000-0000-4000-8000-000000000001", assetCode: "EQ-CNC-001", assetName: "一号加工中心",
  workCenterCode: "WC-CNC-01", workCenterName: "数控中心", location: "机加车间 A-01",
  windowStart: "2026-08-26T00:00:00Z", windowEnd: "2026-08-26T08:00:00Z", plannedProductionMinutes: 480,
  downtimeMinutes: 40, runMinutes: 440, idealCycleSeconds: 60, totalCount: 400, goodCount: 390,
  availabilityRate: 91.6667, performanceRate: 90.9091, qualityRate: 97.5, oeeRate: 81.25,
  shiftName: "白班", productionReference: "PO-001", sourceType: "MANUAL_VERIFIED", sourceReference: "班组核实表",
  status: "DRAFT", rejectionReason: null, version: 2, createdBy: "20000000-0000-4000-8000-000000000001",
  createdAt: "2026-08-26T08:10:00Z", submittedBy: null, submittedAt: null, approvedBy: null, approvedAt: null,
  rejectedBy: null, rejectedAt: null, updatedAt: "2026-08-26T08:20:00Z",
  availableActions: ["UPDATE", "ADD_DOWNTIME", "UPDATE_DOWNTIME", "REMOVE_DOWNTIME", "SUBMIT"],
  downtimes: [{ id: "df000000-0000-4000-8000-000000000001", startedAt: "2026-08-26T02:00:00Z",
    endedAt: "2026-08-26T02:40:00Z", durationMinutes: 40, reasonCategory: "EQUIPMENT_FAILURE",
    responsibleParty: "设备组", description: "主轴异常停机排查", createdBy: "20000000-0000-4000-8000-000000000001",
    createdAt: "2026-08-26T08:15:00Z", updatedBy: "20000000-0000-4000-8000-000000000001",
    updatedAt: "2026-08-26T08:15:00Z" }],
  events: [{ id: "da000000-0000-4000-8000-000000000001", actorUserId: "20000000-0000-4000-8000-000000000001",
    action: "DOWNTIME_ADDED", fromStatus: "DRAFT", toStatus: "DRAFT", reason: "登记停机责任证据",
    requestId: "oee-stop-001", details: { category: "EQUIPMENT_FAILURE" }, occurredAt: "2026-08-26T08:15:00Z" }],
};
const page = { items: [record], totalElements: 1, page: 0, size: 100, totalPages: 1, approvedRecordCount: 0,
  averageAvailabilityRate: 0, averagePerformanceRate: 0, averageQualityRate: 0, averageOeeRate: 0,
  canMaintain: true, canApprove: true };
const assets = { items: [], totalElements: 0, page: 0, size: 100, totalPages: 0, canMaintain: true };

afterEach(() => vi.unstubAllGlobals());

describe("equipment OEE client service", () => {
  it("parses manual source, four metrics and downtime evidence", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ data: { page, assets, requestId: "oee-page-001" } })));
    const result = await refreshEquipmentOeePage();
    expect(result.page.items[0]).toEqual(expect.objectContaining({ sourceType: "MANUAL_VERIFIED", oeeRate: 81.25 }));
    expect(equipmentOeePageSchema.parse(page).canApprove).toBe(true);
  });

  it("loads detail with classified downtime and immutable request evidence", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ record })));
    await expect(loadEquipmentOeeDetail(record.id)).resolves.toEqual(record);
    expect(equipmentOeeRecordSchema.parse(record).events[0].requestId).toBe("oee-stop-001");
  });

  it("sends complete manual create and optimistic-lock action payloads", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(Response.json({ record })).mockResolvedValueOnce(Response.json({ record }));
    vi.stubGlobal("fetch", fetchMock);
    await submitEquipmentOeeMutation({ operation: "create", assetId: record.assetId, windowStart: record.windowStart,
      windowEnd: record.windowEnd, plannedProductionMinutes: 480, idealCycleSeconds: 60, totalCount: 400,
      goodCount: 390, shiftName: "白班", reason: "建立人工核实 OEE 记录" });
    await submitEquipmentOeeMutation({ operation: "act", id: record.id, action: "SUBMIT", expectedVersion: 2,
      reason: "提交已经核实的统计口径" });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(expect.objectContaining({ operation: "create", totalCount: 400 }));
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(expect.objectContaining({ action: "SUBMIT", expectedVersion: 2 }));
  });

  it("surfaces conflict with request evidence", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ message: "OEE 记录已被其他用户修改",
      requestId: "oee-conflict-001" }, { status: 409 })));
    await expect(submitEquipmentOeeMutation({ operation: "act", id: record.id, action: "SUBMIT", expectedVersion: 1,
      reason: "提交已经核实的统计口径" })).rejects.toEqual(
      expect.objectContaining<Partial<EquipmentOeeClientError>>({ status: 409, requestId: "oee-conflict-001" }));
  });
});
