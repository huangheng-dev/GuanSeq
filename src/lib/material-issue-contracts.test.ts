import { describe, expect, it } from "vitest";
import { materialIssueRecordSchema, materialIssueReferenceDataSchema } from "@/lib/contracts";

describe("生产备料与领退料契约", () => {
  it("接受完整领料单、退料、流水证据和参考数据", () => {
    const issue = materialIssueRecordSchema.parse({
      id: "61000000-0000-4000-8000-000000000001",
      issueNumber: "PI-20260818-000001",
      productionOrderId: "91000000-0000-4000-8000-000000000001",
      orderNumber: "MO-260815-012",
      materialId: "41000000-0000-4000-8000-000000000001",
      materialCode: "GS-800",
      materialName: "智能贯序控制器",
      materialSpecification: null,
      unit: "台",
      plannedQuantity: 8,
      warehouseId: "71000000-0000-4000-8000-000000000001",
      warehouseCode: "WH-RM",
      warehouseName: "原材料仓",
      status: "PARTIAL",
      cancellationReason: null,
      version: 1,
      createdAt: "2026-08-18T01:00:00Z",
      updatedAt: "2026-08-18T01:10:00Z",
      lines: [{
        id: "62000000-0000-4000-8000-000000000001", lineNumber: 10,
        componentMaterialId: "42000000-0000-4000-8000-000000000002",
        componentMaterialCode: "PM-45", componentMaterialName: "精密传动模组", componentMaterialSpecification: "45mm",
        unit: "套", requiredQuantity: 8.16, issuedQuantity: 1, returnedQuantity: 0, issuableQuantity: 7.16, bomNote: "损耗 2%", version: 1,
      }],
      returns: [{
        id: "63000000-0000-4000-8000-000000000001", returnNumber: "RT-20260818-000001",
        issueId: "61000000-0000-4000-8000-000000000001", issueNumber: "PI-20260818-000001",
        productionOrderId: "91000000-0000-4000-8000-000000000001", orderNumber: "MO-260815-012",
        warehouseId: "71000000-0000-4000-8000-000000000001", warehouseCode: "WH-RM", warehouseName: "原材料仓",
        locationId: "72000000-0000-4000-8000-000000000001", locationCode: "A-01-03", locationName: "原料 A 区",
        reason: "余料退回", createdAt: "2026-08-18T02:00:00Z",
        lines: [{ id: "63000000-0000-4000-8000-000000000002", issueLineId: "62000000-0000-4000-8000-000000000001", lineNumber: 10, componentMaterialId: "42000000-0000-4000-8000-000000000002", componentMaterialCode: "PM-45", componentMaterialName: "精密传动模组", componentMaterialSpecification: "45mm", unit: "套", quantity: 0.5, reason: "剩余退回" }],
      }],
      events: [{ id: "64000000-0000-4000-8000-000000000001", action: "ISSUE", fromStatus: "DRAFT", toStatus: "PARTIAL", source: "MOBILE_SCAN", requestId: "req-1", occurredAt: "2026-08-18T01:10:00Z" }],
      stockTransactions: [{
        id: "65000000-0000-4000-8000-000000000001", issueLineId: "62000000-0000-4000-8000-000000000001", returnLineId: null,
        movementType: "ISSUE", componentMaterialCode: "PM-45", quantity: 1, warehouseId: "71000000-0000-4000-8000-000000000001", warehouseCode: "WH-RM", warehouseName: "原材料仓",
        locationId: "72000000-0000-4000-8000-000000000001", locationCode: "A-01-03", locationName: "原料 A 区",
        balanceId: "73000000-0000-4000-8000-000000000001", lotNumber: "LOT-2401", movementId: "81000000-0000-4000-8000-000000000001", movementNumber: "MOV-20260818-000123", source: "MOBILE_SCAN", requestId: "req-1", occurredAt: "2026-08-18T01:10:00Z",
      }],
    });
    expect(issue.lines[0]?.issuableQuantity).toBe(7.16);
    expect(issue.stockTransactions[0]?.movementNumber).toBe("MOV-20260818-000123");

    const reference = materialIssueReferenceDataSchema.parse({
      canControl: true,
      productionOrders: [{ id: "91000000-0000-4000-8000-000000000001", orderNumber: "MO-1", materialId: "41000000-0000-4000-8000-000000000001", materialCode: "GS-800", materialName: "控制器", materialSpecification: null, unit: "台", plannedQuantity: 8, plannedStartDate: "2026-08-15", workshop: "总装一车间", owner: "林浩" }],
      warehouses: [{ id: "71000000-0000-4000-8000-000000000001", code: "WH-RM", name: "原材料仓" }],
      locations: [{ id: "72000000-0000-4000-8000-000000000001", warehouseId: "71000000-0000-4000-8000-000000000001", code: "A-01-03", name: "原料 A 区", locationType: "RAW_MATERIAL" }],
      availableStocks: [{ id: "73000000-0000-4000-8000-000000000001", warehouseId: "71000000-0000-4000-8000-000000000001", warehouseCode: "WH-RM", locationId: "72000000-0000-4000-8000-000000000001", locationCode: "A-01-03", locationName: "原料 A 区", materialId: "42000000-0000-4000-8000-000000000002", materialCode: "PM-45", lotNumber: "LOT-2401", availableQuantity: 12, version: 4 }],
    });
    expect(reference.locations[0]?.warehouseId).toBe("71000000-0000-4000-8000-000000000001");
  });
});
