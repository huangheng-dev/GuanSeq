import { describe, expect, it } from "vitest";
import { inventoryRecordSchema, mrpRunRecordSchema } from "./contracts";

const balance = {
  id: "73000000-0000-4000-8000-000000000001", warehouseId: "71000000-0000-4000-8000-000000000003",
  warehouseCode: "WH-FG", warehouseName: "成品仓", locationId: "72000000-0000-4000-8000-000000000004",
  locationCode: "FG-01", locationName: "成品一号库位", materialId: "42000000-0000-4000-8000-000000000001",
  materialCode: "GS-800", materialName: "伺服驱动控制柜", materialSpecification: "GS-800 标准型", unit: "台",
  lotNumber: "LOT-GS-2608A", qualityStatus: "AVAILABLE", onHandQuantity: 12, allocatedQuantity: 6, frozenQuantity: 0,
  availableQuantity: 6, version: 0, updatedAt: "2026-08-15T02:00:00Z", movements: [{
    id: "74000000-0000-4000-8000-000000000001", movementNumber: "MOV-260815-001", movementType: "RECEIPT",
    quantity: 12, reason: "期初库存", requestId: "seed-stock-gs800", beforeOnHand: 0, afterOnHand: 12,
    beforeAllocated: 0, afterAllocated: 0, beforeFrozen: 0, afterFrozen: 0, occurredAt: "2026-08-15T02:00:00Z",
  }],
};

describe("inventory and MRP supply contracts", () => {
  it("accepts a traceable versioned inventory balance", () => {
    expect(inventoryRecordSchema.parse(balance).movements[0].requestId).toBe("seed-stock-gs800");
  });

  it("rejects negative balances and unknown movement types", () => {
    expect(inventoryRecordSchema.safeParse({ ...balance, availableQuantity: -1 }).success).toBe(false);
    expect(inventoryRecordSchema.safeParse({ ...balance, movements: [{ ...balance.movements[0], movementType: "ADJUST" }] }).success).toBe(false);
  });

  it("requires frozen inventory supply snapshots on MRP runs", () => {
    const run = { id: "55000000-0000-4000-8000-000000000001", runNumber: "MRP-260815-001", name: "滚动检查", horizonStart: "2026-08-15", horizonEnd: "2026-09-30", status: "BLOCKED", demandCount: 1, totalQuantity: 6, exceptionCount: 1, startedAt: "2026-08-15T02:10:00Z", finishedAt: "2026-08-15T02:10:01Z", requestId: "mrp-test", version: 0, demands: [], supplies: [{ id: "57000000-0000-4000-8000-000000000001", materialId: balance.materialId, materialCode: balance.materialCode, materialName: balance.materialName, unit: balance.unit, onHandQuantity: 12, allocatedQuantity: 6, frozenQuantity: 0, availableQuantity: 6, balanceCount: 1, snapshottedAt: "2026-08-15T02:10:00Z" }], scheduledReceipts: [], netRequirements: [], exceptions: [{ id: "58000000-0000-4000-8000-000000000001", code: "SCHEDULED_RECEIPTS_UNAVAILABLE", severity: "BLOCKER", materialId: null, materialCode: null, materialName: null, message: "历史计划接收未接入", resolutionPath: "/procurement/orders", createdAt: "2026-08-15T02:10:00Z" }] };
    expect(mrpRunRecordSchema.parse(run).supplies[0].availableQuantity).toBe(6);
    expect(mrpRunRecordSchema.safeParse({ ...run, supplies: undefined }).success).toBe(false);
  });
});
