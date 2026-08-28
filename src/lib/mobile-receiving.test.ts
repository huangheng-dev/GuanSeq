import { describe, expect, it } from "vitest";

import { resolveMaterialScan, resolvePurchaseOrderScan } from "./mobile-receiving";

const orders = [{
  id: "82000000-0000-4000-8000-000000000001",
  orderNumber: "PO-20260827-000001",
  supplierId: "81000000-0000-4000-8000-000000000002",
  supplierCode: "SUP-0002",
  supplierName: "联创工业组件",
  promisedReceiptDate: "2026-08-30",
  lines: [{
    id: "83000000-0000-4000-8000-000000000001",
    lineNumber: 1,
    materialId: "42000000-0000-4000-8000-000000000004",
    materialCode: "PK-GS800",
    materialName: "GS-800 包装箱",
    materialSpecification: null,
    unit: "个",
    orderedQuantity: 20,
    receivedQuantity: 5,
    outstandingQuantity: 15,
    inspectionRequired: false,
  }],
}];

describe("mobile receiving scan resolution", () => {
  it("accepts keyboard-scanner text with optional portable prefixes", () => {
    const resolvedOrder = resolvePurchaseOrderScan(orders, " PO:po-20260827-000001\n");
    expect(resolvedOrder.order?.supplierCode).toBe("SUP-0002");
    expect(resolveMaterialScan(resolvedOrder.order!, "MAT|pk-gs800").line?.outstandingQuantity).toBe(15);
  });

  it("fails closed instead of guessing an order or material", () => {
    expect(resolvePurchaseOrderScan(orders, "PO-UNKNOWN").error).toContain("未找到");
    expect(resolveMaterialScan(orders[0], "UNKNOWN").error).toContain("不属于");
  });
});
