import { afterEach, describe, expect, it, vi } from "vitest";

import type { EquipmentAsset, EquipmentWorkOrder } from "@/lib/contracts";
import { EquipmentWorkOrderClientError, loadEquipmentWorkOrderDetail, refreshEquipmentWorkOrders, submitEquipmentWorkOrderMutation } from "./equipment-work-order-client-service";

const asset: EquipmentAsset = { id: "a1000000-0000-4000-8000-000000000001", assetCode: "EQ-CNC-001", assetName: "加工中心", category: "PRODUCTION", manufacturer: null, model: null, serialNumber: null, workCenterCode: null, workCenterName: null, location: "机加车间", responsiblePerson: "周凯", commissioningDate: null, operatingStatus: "DOWN", statusChangedAt: "2026-08-25T01:00:00Z", version: 1, createdAt: "2026-08-20T01:00:00Z", updatedAt: "2026-08-25T01:00:00Z", events: [] };
const workOrder: EquipmentWorkOrder = { id: "b1000000-0000-4000-8000-000000000001", workOrderNumber: "WO-20260825-0001", workType: "REPAIR", sourceType: "BREAKDOWN", sourceWorkOrderId: null, assetId: asset.id, assetCode: asset.assetCode, assetName: asset.assetName, assetLocation: asset.location, assetOperatingStatus: "DOWN", assetVersion: 1, title: "故障维修", description: "排查设备故障", priority: "HIGH", status: "PLANNED", plannedStartAt: "2026-08-25T02:00:00Z", dueAt: "2026-08-25T10:00:00Z", assignee: "周凯", outcome: null, completionNotes: null, startedAt: null, submittedAt: null, completedAt: null, version: 0, createdAt: "2026-08-25T01:00:00Z", updatedAt: "2026-08-25T01:00:00Z", availableActions: ["START", "CANCEL"], events: [] };
const page = { items: [workOrder], totalElements: 1, page: 0, size: 200, totalPages: 1, canMaintain: true };

afterEach(() => vi.unstubAllGlobals());

describe("equipment work order client service", () => {
  it("parses page, asset references and detail", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(Response.json({ page, assets: [asset] })).mockResolvedValueOnce(Response.json({ workOrder }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(refreshEquipmentWorkOrders()).resolves.toEqual({ page, assets: [asset] });
    await expect(loadEquipmentWorkOrderDetail(workOrder.id)).resolves.toEqual(workOrder);
  });

  it("sends both work order and asset optimistic versions", async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ workOrder: { ...workOrder, status: "IN_PROGRESS", version: 1, assetOperatingStatus: "MAINTENANCE", assetVersion: 2 } }));
    vi.stubGlobal("fetch", fetchMock);
    await submitEquipmentWorkOrderMutation({ operation: "action", id: workOrder.id, action: "START", reason: "维修人员开始执行", expectedVersion: 0, assetExpectedVersion: 1 });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ operation: "action", id: workOrder.id, action: "START", reason: "维修人员开始执行", expectedVersion: 0, assetExpectedVersion: 1 });
  });

  it("retains backend conflict evidence", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ message: "关联设备状态已变化", requestId: "req-work-order-conflict" }, { status: 409 })));
    await expect(submitEquipmentWorkOrderMutation({ operation: "action", id: workOrder.id, action: "START", reason: "维修人员开始执行", expectedVersion: 0, assetExpectedVersion: 1 })).rejects.toEqual(expect.objectContaining<Partial<EquipmentWorkOrderClientError>>({ status: 409, requestId: "req-work-order-conflict" }));
  });
});
