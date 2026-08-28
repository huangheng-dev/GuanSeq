import { describe, expect, it } from "vitest";
import { operationLaborEntrySchema, operationTaskRecordSchema } from "@/lib/contracts";

describe("车间工序执行契约", () => {
  it("接受工序快照、执行数量、检验点、请求号和审计事件", () => {
    const task = operationTaskRecordSchema.parse({
      id: "93000000-0000-4000-8000-000000000001",
      taskNumber: "OT-260815-900001",
      orderId: "91000000-0000-4000-8000-000000000001",
      orderNumber: "MO-260815-012",
      materialId: "42000000-0000-4000-8000-000000000001",
      materialCode: "GS-800",
      materialName: "伺服驱动控制柜",
      materialSpecification: "GS-800 标准型",
      unit: "台",
      plannedQuantity: 8,
      workshop: "总装一车间",
      routingId: "64000000-0000-4000-8000-000000000001",
      routingNumber: "RTG-260815-001",
      routingVersionCode: "V1.0",
      sourceOperationId: "65000000-0000-4000-8000-000000000001",
      sequenceNumber: 10,
      operationCode: "OP-ASM",
      operationName: "机械装配",
      workCenterCode: "WC-ASM-01",
      workCenterName: "总装工作中心",
      setupMinutes: 30,
      runMinutesPerUnit: 45,
      queueMinutes: 10,
      inspectionRequired: false,
      instructionSummary: "按装配图完成柜体、传动组件和紧固件装配",
      status: "COMPLETED",
      startedAt: "2026-08-16T01:00:00Z",
      completedAt: "2026-08-16T03:10:00Z",
      completedQuantity: 2,
      shiftName: "白班",
      operatorName: "陈磊",
      note: "首批 2 台完工",
      version: 2,
      createdAt: "2026-08-15T03:05:00Z",
      updatedAt: "2026-08-16T03:10:00Z",
      events: [
        { id: "94000000-0000-4000-8000-000000000001", action: "CREATED", fromStatus: null, toStatus: "PENDING", requestId: "seed-operation-tasks", comment: null, source: "SYSTEM", occurredAt: "2026-08-15T03:05:00Z" },
        { id: "94000000-0000-4000-8000-000000000002", action: "START", fromStatus: "PENDING", toStatus: "IN_PROGRESS", requestId: "seed-op-start-001", comment: "白班开工", source: "DESKTOP_FORM", occurredAt: "2026-08-16T01:00:00Z" },
        { id: "94000000-0000-4000-8000-000000000003", action: "COMPLETE", fromStatus: "IN_PROGRESS", toStatus: "COMPLETED", requestId: "seed-op-complete-001", comment: "首批完工", source: "DESKTOP_FORM", occurredAt: "2026-08-16T03:10:00Z" },
      ],
    });

    expect(task.status).toBe("COMPLETED");
    expect(task.completedQuantity).toBe(2);
    expect(task.events).toHaveLength(3);
    expect(operationTaskRecordSchema.safeParse({ ...task, completedQuantity: 0 }).success).toBe(false);
  });

  it("接受实际人工工时登记、审核和审计证据", () => {
    const entry = operationLaborEntrySchema.parse({
      id: "97000000-0000-4000-8000-000000000001",
      entryNumber: "LAB-260816-900001",
      taskId: "93000000-0000-4000-8000-000000000001",
      taskNumber: "OT-260815-900001",
      orderId: "91000000-0000-4000-8000-000000000001",
      orderNumber: "MO-260815-012",
      operationCode: "OP-ASM",
      operationName: "机械装配",
      workCenterCode: "WC-ASM-01",
      workCenterName: "总装工作中心",
      workDate: "2026-08-16",
      shiftName: "白班",
      operatorName: "陈磊",
      actualMinutes: 120,
      status: "APPROVED",
      note: "首批机械装配实际人工",
      approvedBy: "20000000-0000-4000-8000-000000000001",
      approvedAt: "2026-08-16T03:20:00Z",
      voidedBy: null,
      voidedAt: null,
      voidReason: null,
      version: 1,
      createdAt: "2026-08-16T03:15:00Z",
      updatedAt: "2026-08-16T03:20:00Z",
      events: [
        { id: "97100000-0000-4000-8000-000000000001", action: "RECORDED", fromStatus: null, toStatus: "RECORDED", requestId: "seed-labor-record-001", comment: null, occurredAt: "2026-08-16T03:15:00Z" },
        { id: "97100000-0000-4000-8000-000000000002", action: "APPROVED", fromStatus: "RECORDED", toStatus: "APPROVED", requestId: "seed-labor-approve-001", comment: "生产负责人审核", occurredAt: "2026-08-16T03:20:00Z" },
      ],
    });
    expect(entry.status).toBe("APPROVED");
    expect(entry.actualMinutes).toBe(120);
    expect(operationLaborEntrySchema.safeParse({ ...entry, actualMinutes: 1441 }).success).toBe(false);
  });
});
