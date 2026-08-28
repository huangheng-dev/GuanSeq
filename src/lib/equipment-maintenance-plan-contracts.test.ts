import { describe, expect, it } from "vitest";

import { equipmentMaintenanceGenerationSchema, equipmentMaintenancePlanPageSchema, equipmentMaintenancePlanSchema } from "./contracts";

const plan = {
  id: "d1000000-0000-4000-8000-000000000001", planCode: "PLAN-CNC-WEEKLY", name: "加工中心每周点检",
  workType: "INSPECTION", assetId: "a1000000-0000-4000-8000-000000000001", assetCode: "EQ-CNC-001",
  assetName: "一号精密加工中心", assetLocation: "机加车间 A-01", description: "检查联锁与润滑状态",
  priority: "MEDIUM", intervalDays: 7, leadDays: 3, firstDueDate: "2026-08-27", nextDueDate: "2026-08-27",
  nextGenerationDate: "2026-08-24", plannedStartTime: "08:30:00", dueTime: "11:30:00", assignee: "周凯",
  status: "ACTIVE", generationStatus: "DUE", overdueWorkOrderCount: 0, overdueWorkOrderNumbers: [], version: 0,
  createdAt: "2026-08-25T02:00:00Z", updatedAt: "2026-08-25T02:00:00Z", availableActions: ["INACTIVATE"], events: [],
} as const;

describe("equipment maintenance plan contracts", () => {
  it("parses schedule, lead time and overdue evidence", () => {
    expect(equipmentMaintenancePlanSchema.parse(plan).nextGenerationDate).toBe("2026-08-24");
    expect(equipmentMaintenancePlanPageSchema.parse({ items: [plan], totalElements: 1, page: 0, size: 200,
      totalPages: 1, activeCount: 1, generationDueCount: 1, overdueWorkOrderCount: 0, canMaintain: true,
      recentRuns: [] }).generationDueCount).toBe(1);
  });

  it("rejects invalid intervals and parses generation evidence", () => {
    expect(() => equipmentMaintenancePlanSchema.parse({ ...plan, intervalDays: 0 })).toThrow();
    expect(equipmentMaintenanceGenerationSchema.parse({ id: "e1000000-0000-4000-8000-000000000001",
      requestId: "generate-1", asOfDate: "2026-08-25", reason: "生成到期任务", status: "COMPLETED",
      generatedCount: 1, existingCount: 0, skippedCount: 0, actorUserId: "20000000-0000-4000-8000-000000000001",
      startedAt: "2026-08-25T03:00:00Z", completedAt: "2026-08-25T03:00:01Z", items: [] }).generatedCount).toBe(1);
  });
});
