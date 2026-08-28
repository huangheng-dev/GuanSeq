import { describe, expect, it } from "vitest";
import { finalInspectionRecordSchema, mrpNetRequirementSchema, productionOrderRecordSchema, productionWorkReportRecordSchema } from "./contracts";

const order = {
  id: "91000000-0000-4000-8000-000000000001", orderNumber: "MO-260815-012",
  materialId: "42000000-0000-4000-8000-000000000001", materialCode: "GS-800", materialName: "伺服驱动控制柜",
  materialSpecification: "GS-800 标准型", unit: "台", plannedQuantity: 8, completedQuantity: 2, reportedQuantity: 0, reportableQuantity: 6, outstandingQuantity: 6,
  plannedStartDate: "2026-08-16", plannedReceiptDate: "2026-08-21", workshop: "总装一车间", owner: "周启明",
  sourceType: "SALES_ORDER", sourceId: "51000000-0000-4000-8000-000000000004", sourceNumber: "SO-260815-004",
  status: "RELEASED", cancellationReason: null, version: 0, updatedAt: "2026-08-15T03:00:00Z",
};

describe("production and MRP netting contracts", () => {
  it("accepts a released production order with explicit outstanding quantity", () => {
    expect(productionOrderRecordSchema.parse(order).outstandingQuantity).toBe(6);
    expect(productionOrderRecordSchema.safeParse({ ...order, completedQuantity: -1 }).success).toBe(false);
  });

  it("accepts an auditable production recommendation", () => {
    const result = mrpNetRequirementSchema.parse({ id: crypto.randomUUID(), requirementLevel: 0, sourceType: "INDEPENDENT_DEMAND", parentMaterialId: null, parentMaterialCode: null, materialId: order.materialId, materialCode: order.materialCode, materialName: order.materialName, procurementType: "MAKE", unit: "台", grossQuantity: 30, availableConsumed: 6, scheduledReceiptConsumed: 6, netQuantity: 18, requiredDate: "2026-08-22", recommendedReleaseDate: "2026-08-17", recommendationType: "PRODUCTION", decisionStatus: "PROPOSED", convertedOrderType: null, convertedOrderId: null, convertedOrderNumber: null, version: 0, createdAt: "2026-08-15T03:10:00Z" });
    expect(result.netQuantity).toBe(18);
  });

  it("accepts the report, inspection and receipt evidence chain", () => {
    const reportId = crypto.randomUUID(); const inspectionId = crypto.randomUUID();
    const inspection = finalInspectionRecordSchema.parse({ id: inspectionId, inspectionNumber: "FQI-20260816-000001", sourceType: "PRODUCTION_REPORT", sourceId: reportId, sourceNumber: "RPT-20260816-000001", orderId: order.id, orderNumber: order.orderNumber, materialId: order.materialId, materialCode: order.materialCode, materialName: order.materialName, materialSpecification: order.materialSpecification, unit: order.unit, inspectionQuantity: 3, status: "COMPLETED", result: "PARTIALLY_PASSED", acceptedQuantity: 2, rejectedQuantity: 1, inspector: "吴倩", defectDescription: "端子扭矩不合格", conclusion: "两台放行，一台返修", version: 1, createdAt: "2026-08-16T00:00:00Z", completedAt: "2026-08-16T00:10:00Z" });
    const report = productionWorkReportRecordSchema.parse({ id: reportId, reportNumber: inspection.sourceNumber, orderId: order.id, orderNumber: order.orderNumber, materialId: order.materialId, materialCode: order.materialCode, materialName: order.materialName, materialSpecification: order.materialSpecification, unit: order.unit, workshop: "总装一车间", shiftName: "白班", operatorName: "陈磊", reportedQuantity: 3, note: null, inspectionId, inspectionNumber: inspection.inspectionNumber, inspectionStatus: inspection.status, qualityResult: inspection.result, acceptedQuantity: 2, rejectedQuantity: 1, receiptBalanceId: crypto.randomUUID(), receiptMovementId: crypto.randomUUID(), receiptWarehouse: "成品仓", receiptLocation: "成品一号库位", lotNumber: "LOT-GS-20260816", status: "RECEIVED", operationTaskId: null, operationTaskNumber: null, source: "DESKTOP_FORM", version: 2, createdAt: "2026-08-16T00:00:00Z", settledAt: "2026-08-16T00:12:00Z" });
    expect(report.acceptedQuantity).toBe(2);
    expect(report.status).toBe("RECEIVED");
  });
});
