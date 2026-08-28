import { describe, expect, it } from "vitest";
import type { InventoryControlReferenceData } from "./inventory-control-contracts";
import { resolveInventoryControlBalance, resolveInventoryControlTarget } from "./inventory-control-scan";

const references: InventoryControlReferenceData = {
  balances: [{ id: "73000000-0000-4000-8000-000000000003", version: 0, warehouseId: "71000000-0000-4000-8000-000000000001",
    warehouseCode: "WH-RM", warehouseName: "原材料仓", locationId: "72000000-0000-4000-8000-000000000001", locationCode: "A-01-03",
    locationName: "原料库位", locationType: "STORAGE", materialCode: "BR-6204", materialName: "轴承", materialSpecification: null,
    lotNumber: "LOT-01", unit: "件", qualityStatus: "AVAILABLE", onHandQuantity: 10, allocatedQuantity: 0, frozenQuantity: 0,
    availableQuantity: 10, reservedTransferQuantity: 0, activeCount: false }],
  targetLocations: [{ id: "72000000-0000-4000-8000-000000000012", warehouseId: "71000000-0000-4000-8000-000000000001",
    warehouseCode: "WH-RM", code: "A-01-04", name: "目标库位", scanCode: "LOC:A-01-04" }],
};

describe("inventory control scan", () => {
  it("only resolves an exact STOCK payload", () => {
    expect(resolveInventoryControlBalance("STOCK:73000000-0000-4000-8000-000000000003", references)?.materialCode).toBe("BR-6204");
    expect(resolveInventoryControlBalance("73000000-0000-4000-8000-000000000003", references)).toBeNull();
    expect(resolveInventoryControlBalance("STOCK:73000000", references)).toBeNull();
  });
  it("resolves LOC or direct location code only in the selected warehouse", () => {
    expect(resolveInventoryControlTarget("LOC:A-01-04", "71000000-0000-4000-8000-000000000001", references)?.code).toBe("A-01-04");
    expect(resolveInventoryControlTarget("a-01-04", "71000000-0000-4000-8000-000000000001", references)?.code).toBe("A-01-04");
    expect(resolveInventoryControlTarget("A-01-04", "71000000-0000-4000-8000-000000000099", references)).toBeNull();
  });
});
