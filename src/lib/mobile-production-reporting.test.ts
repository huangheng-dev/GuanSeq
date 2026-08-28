import { describe, expect, it } from "vitest";
import type { OperationTaskRecord, ProductionOrderRecord } from "@/lib/contracts";
import { deriveMobileReportingAction, resolveMobileOperationTaskScan, resolveMobileOperatorScan } from "./mobile-production-reporting";

const task = (overrides: Partial<OperationTaskRecord> = {}): OperationTaskRecord => ({
  id: "81000000-0000-4000-8000-000000000001", taskNumber: "OT-20260827-000001",
  orderId: "91000000-0000-4000-8000-000000000001", orderNumber: "MO-001",
  materialId: "42000000-0000-4000-8000-000000000001", materialCode: "FG-01", materialName: "测试产品",
  materialSpecification: null, unit: "台", plannedQuantity: 8, workshop: "总装一车间",
  routingId: "61000000-0000-4000-8000-000000000001", routingNumber: "RT-01", routingVersionCode: "V1",
  sourceOperationId: "62000000-0000-4000-8000-000000000001", sequenceNumber: 10,
  operationCode: "OP10", operationName: "装配", workCenterCode: "WC-01", workCenterName: "装配中心",
  setupMinutes: 10, runMinutesPerUnit: 3, queueMinutes: 0, inspectionRequired: false, instructionSummary: null,
  status: "PENDING", startedAt: null, completedAt: null, completedQuantity: null, shiftName: null,
  operatorName: null, note: null, version: 0, createdAt: "2026-08-27T08:00:00Z", updatedAt: "2026-08-27T08:00:00Z",
  events: [{ id: "82000000-0000-4000-8000-000000000001", action: "CREATED", fromStatus: null, toStatus: "PENDING",
    requestId: "create-1", comment: null, source: "SYSTEM", occurredAt: "2026-08-27T08:00:00Z" }], ...overrides,
});

const order = (overrides: Partial<ProductionOrderRecord> = {}): ProductionOrderRecord => ({
  id: "91000000-0000-4000-8000-000000000001", orderNumber: "MO-001",
  materialId: "42000000-0000-4000-8000-000000000001", materialCode: "FG-01", materialName: "测试产品",
  materialSpecification: null, unit: "台", plannedQuantity: 8, completedQuantity: 0, reportedQuantity: 0,
  reportableQuantity: 8, outstandingQuantity: 8, plannedStartDate: "2026-08-27", plannedReceiptDate: "2026-08-30",
  workshop: "总装一车间", owner: "lin.hao", sourceType: "MANUAL", sourceId: null, sourceNumber: null,
  status: "IN_PROGRESS", cancellationReason: null, version: 2, updatedAt: "2026-08-27T08:00:00Z", ...overrides,
});

describe("mobile production reporting", () => {
  it("resolves task number and exact current operator badge", () => {
    expect(resolveMobileOperationTaskScan([task()], "OT:OT-20260827-000001").task?.id).toBe(task().id);
    expect(resolveMobileOperatorScan("EMP:lin.hao", "lin.hao").username).toBe("lin.hao");
    expect(resolveMobileOperatorScan("EMP:other", "lin.hao").error).toContain("不能代替他人");
  });

  it("derives start, completion and final production report stages", () => {
    expect(deriveMobileReportingAction(task(), [order({ status: "RELEASED", version: 1 })], [task()]).action).toBe("START");
    expect(deriveMobileReportingAction(task({ status: "IN_PROGRESS", version: 1 }), [order()], [task()]).action).toBe("COMPLETE");
    const first = task({ status: "COMPLETED", completedQuantity: 8, sequenceNumber: 10 });
    const final = task({ id: "81000000-0000-4000-8000-000000000003", taskNumber: "OT-20260827-000003",
      sourceOperationId: "62000000-0000-4000-8000-000000000003", status: "COMPLETED", completedQuantity: 8, sequenceNumber: 30 });
    expect(deriveMobileReportingAction(first, [order()], [first, final]).action).toBe("WAIT");
    expect(deriveMobileReportingAction(final, [order()], [first, final]).action).toBe("REPORT");
  });

  it("does not offer another report while all quantity is already reserved", () => {
    const final = task({ status: "COMPLETED", completedQuantity: 8 });
    expect(deriveMobileReportingAction(final, [order({ reportableQuantity: 0, reportedQuantity: 8 })], [final]).action).toBe("DONE");
  });
});
