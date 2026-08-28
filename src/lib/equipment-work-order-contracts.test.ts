import { describe, expect, it } from "vitest";

import { equipmentWorkOrderPageSchema, equipmentWorkOrderSchema } from "./contracts";

const workOrder = {
  id: "b1000000-0000-4000-8000-000000000001", workOrderNumber: "WO-20260825-0001", workType: "REPAIR",
  sourceType: "INSPECTION_FAILURE", sourceWorkOrderId: "b1000000-0000-4000-8000-000000000002", sourcePlanId: null, sourceDueDate: null,
  assetId: "a1000000-0000-4000-8000-000000000001", assetCode: "EQ-CNC-001", assetName: "加工中心",
  assetLocation: "机加车间", assetOperatingStatus: "MAINTENANCE", assetVersion: 2, title: "温升异常维修",
  description: "检查温度传感器和主轴润滑", priority: "HIGH", status: "WAITING_ACCEPTANCE",
  plannedStartAt: "2026-08-25T02:00:00Z", dueAt: "2026-08-25T10:00:00Z", assignee: "周凯",
  outcome: null, completionNotes: "更换传感器并完成负载验证", startedAt: "2026-08-25T02:10:00Z",
  submittedAt: "2026-08-25T04:00:00Z", completedAt: null, version: 2,
  createdAt: "2026-08-25T01:00:00Z", updatedAt: "2026-08-25T04:00:00Z",
  costEvidence: { spareCost: 240, laborCost: 200, totalCost: 440, currency: "CNY",
    basis: "备件按领用日有效标准成本；人工按本次登记小时费率估算；不生成财务凭证",
    availableActions: [], spareTransactions: [], laborTransactions: [] }, availableActions: ["ACCEPT", "REJECT"],
  events: [{ id: "b2000000-0000-4000-8000-000000000001", actorUserId: "20000000-0000-4000-8000-000000000001",
    action: "SUBMITTED_FOR_ACCEPTANCE", fromStatus: "IN_PROGRESS", toStatus: "WAITING_ACCEPTANCE",
    reason: "维修完成并提交验收", outcome: null, requestId: "repair-submit-001", details: {}, occurredAt: "2026-08-25T04:00:00Z" }],
};

describe("equipment work order contracts", () => {
  it("parses repair acceptance state and immutable evidence", () => {
    const parsed = equipmentWorkOrderSchema.parse(workOrder);
    expect(parsed.availableActions).toEqual(["ACCEPT", "REJECT"]);
    expect(parsed.events[0].requestId).toBe("repair-submit-001");
    expect(parsed.costEvidence?.totalCost).toBe(440);
    expect(equipmentWorkOrderPageSchema.parse({ items: [workOrder], totalElements: 1, page: 0, size: 200, totalPages: 1, canMaintain: true }).canMaintain).toBe(true);
  });

  it("rejects telemetry status and missing asset concurrency version", () => {
    expect(() => equipmentWorkOrderSchema.parse({ ...workOrder, assetOperatingStatus: "ONLINE" })).toThrow();
    const withoutVersion: Record<string, unknown> = { ...workOrder }; delete withoutVersion.assetVersion;
    expect(() => equipmentWorkOrderSchema.parse(withoutVersion)).toThrow();
  });
});
