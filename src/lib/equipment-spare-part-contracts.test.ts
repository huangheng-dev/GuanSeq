import { describe, expect, it } from "vitest";

import { equipmentMaintenanceCostMutationResultSchema, equipmentSparePartPageSchema, equipmentSparePartReferenceSchema } from "./contracts";

describe("equipment spare part contracts", () => {
  it("parses live stock, finance cost, references and immutable cost evidence", () => {
    const page = equipmentSparePartPageSchema.parse({ items: [{ id: "c1000000-0000-4000-8000-000000000001",
      materialId: "42000000-0000-4000-8000-000000000003", materialCode: "BR-6204", materialName: "深沟球轴承",
      materialSpecification: "6204-2RS", unit: "件", preferredWarehouseId: "71000000-0000-4000-8000-000000000001",
      preferredWarehouseCode: "WH-RM", preferredWarehouseName: "原材料仓", reorderPoint: 120, availableQuantity: 400,
      standardUnitCost: 80, currency: "CNY", costEffectiveDate: "2026-08-15", costStatus: "READY",
      stockStatus: "SUFFICIENT", status: "ACTIVE", version: 0, updatedAt: "2026-08-25T00:45:00Z" }],
      totalElements: 1, page: 0, size: 200, totalPages: 1, canMaintain: true });
    expect(page.items[0].availableQuantity).toBe(400);
    expect(equipmentSparePartReferenceSchema.parse({ materials: [], warehouses: [], locations: [] }).locations).toEqual([]);
    expect(equipmentMaintenanceCostMutationResultSchema.parse({ workOrderVersion: 2, costEvidence: { spareCost: 400,
      laborCost: 0, totalCost: 400, currency: "CNY", basis: "运维估算", availableActions: ["ISSUE_SPARE"],
      spareTransactions: [], laborTransactions: [] } }).costEvidence.totalCost).toBe(400);
  });

  it("rejects missing costs presented as zero and impossible negative available quantities", () => {
    const base = { id: "c1000000-0000-4000-8000-000000000001", materialId: "42000000-0000-4000-8000-000000000003",
      materialCode: "BR-6204", materialName: "深沟球轴承", materialSpecification: null, unit: "件",
      preferredWarehouseId: "71000000-0000-4000-8000-000000000001", preferredWarehouseCode: "WH-RM",
      preferredWarehouseName: "原材料仓", reorderPoint: 10, availableQuantity: 0, standardUnitCost: null, currency: null,
      costEffectiveDate: null, costStatus: "MISSING_COST", stockStatus: "BELOW_REORDER_POINT", status: "ACTIVE", version: 0,
      updatedAt: "2026-08-25T00:45:00Z" };
    expect(equipmentSparePartPageSchema.parse({ items: [base], totalElements: 1, page: 0, size: 10, totalPages: 1, canMaintain: false }).items[0].costStatus).toBe("MISSING_COST");
    expect(() => equipmentSparePartPageSchema.parse({ items: [{ ...base, standardUnitCost: 0 }], totalElements: 1, page: 0, size: 10, totalPages: 1, canMaintain: false })).toThrow();
    expect(() => equipmentSparePartPageSchema.parse({ items: [{ ...base, availableQuantity: -1 }], totalElements: 1, page: 0, size: 10, totalPages: 1, canMaintain: false })).toThrow();
  });
});
