import { afterEach, describe, expect, it, vi } from "vitest";

import type { EquipmentMaintenancePlan } from "@/lib/contracts";
import { EquipmentMaintenancePlanClientError, loadEquipmentMaintenancePlanDetail, refreshEquipmentMaintenancePlans,
  submitEquipmentMaintenancePlanMutation } from "./equipment-maintenance-plan-client-service";

const plan: EquipmentMaintenancePlan = {
  id: "d1000000-0000-4000-8000-000000000001", planCode: "PLAN-CNC-WEEKLY", name: "加工中心每周点检",
  workType: "INSPECTION", assetId: "a1000000-0000-4000-8000-000000000001", assetCode: "EQ-CNC-001",
  assetName: "一号精密加工中心", assetLocation: "机加车间 A-01", description: "检查联锁与润滑状态",
  priority: "MEDIUM", intervalDays: 7, leadDays: 3, firstDueDate: "2026-08-27", nextDueDate: "2026-08-27",
  nextGenerationDate: "2026-08-24", plannedStartTime: "08:30:00", dueTime: "11:30:00", assignee: "周凯",
  status: "ACTIVE", generationStatus: "DUE", overdueWorkOrderCount: 0, overdueWorkOrderNumbers: [], version: 0,
  createdAt: "2026-08-25T02:00:00Z", updatedAt: "2026-08-25T02:00:00Z", availableActions: ["INACTIVATE"], events: [],
};
const page = { items: [plan], totalElements: 1, page: 0, size: 200, totalPages: 1, activeCount: 1,
  generationDueCount: 1, overdueWorkOrderCount: 0, canMaintain: true, recentRuns: [] };

afterEach(() => vi.unstubAllGlobals());

describe("equipment maintenance plan client service", () => {
  it("parses page and detail evidence", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ page })).mockResolvedValueOnce(Response.json({ plan })));
    await expect(refreshEquipmentMaintenancePlans()).resolves.toEqual(page);
    await expect(loadEquipmentMaintenancePlanDetail(plan.id)).resolves.toEqual(plan);
  });

  it("sends immutable schedule inputs and optimistic action version", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(Response.json({ plan })).mockResolvedValueOnce(Response.json({ plan: { ...plan, status: "INACTIVE", version: 1, availableActions: ["ACTIVATE"] } }));
    vi.stubGlobal("fetch", fetchMock);
    await submitEquipmentMaintenancePlanMutation({ operation: "createPlan", planCode: plan.planCode, name: plan.name,
      workType: plan.workType, assetId: plan.assetId, description: plan.description, priority: plan.priority,
      intervalDays: 7, leadDays: 3, firstDueDate: plan.firstDueDate, plannedStartTime: "08:30", dueTime: "11:30",
      assignee: plan.assignee, reason: "建立周期维护模板", assetExpectedVersion: 0 });
    await submitEquipmentMaintenancePlanMutation({ operation: "planAction", id: plan.id, action: "INACTIVATE",
      reason: "试点暂停周期任务", expectedVersion: 0 });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(expect.objectContaining({ intervalDays: 7, leadDays: 3, assetExpectedVersion: 0 }));
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(expect.objectContaining({ action: "INACTIVATE", expectedVersion: 0 }));
  });

  it("parses generation counts and keeps backend conflict request id", async () => {
    const generation = { id: "e1000000-0000-4000-8000-000000000001", requestId: "generate-1", asOfDate: "2026-08-25",
      reason: "生成本周到期任务", status: "COMPLETED", generatedCount: 2, existingCount: 1, skippedCount: 0,
      actorUserId: "20000000-0000-4000-8000-000000000001", startedAt: "2026-08-25T03:00:00Z",
      completedAt: "2026-08-25T03:00:01Z", items: [] };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ generation })).mockResolvedValueOnce(
      Response.json({ message: "模板版本已变化", requestId: "plan-conflict" }, { status: 409 })));
    await expect(submitEquipmentMaintenancePlanMutation({ operation: "generateDue", asOfDate: "2026-08-25",
      reason: "生成本周到期任务" })).resolves.toEqual(generation);
    await expect(submitEquipmentMaintenancePlanMutation({ operation: "planAction", id: plan.id,
      action: "INACTIVATE", reason: "试点暂停周期任务", expectedVersion: 0 })).rejects.toEqual(
      expect.objectContaining<Partial<EquipmentMaintenancePlanClientError>>({ status: 409, requestId: "plan-conflict" }));
  });
});
