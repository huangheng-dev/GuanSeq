import { describe, expect, it } from "vitest";
import { materialPlanningParameterSchema, mrpScheduledReceiptSnapshotSchema, purchaseOrderRecordSchema } from "./contracts";

const order = {
  id: "82000000-0000-4000-8000-000000000001", orderNumber: "PO-260815-001",
  supplierId: "81000000-0000-4000-8000-000000000001", supplierCode: "SUP-0001", supplierName: "海岳精密轴承",
  currency: "CNY", taxRate: 0.13, requestedReceiptDate: "2026-08-20", promisedReceiptDate: "2026-08-20",
  buyer: "唐工", status: "RELEASED", totalNetAmount: 48000, totalTaxAmount: 6240, totalGrossAmount: 54240,
  rejectionReason: null, sourceType: "MANUAL", sourceId: null, sourceNumber: null, version: 0, updatedAt: "2026-08-15T02:00:00Z",
  lines: [{ id: "83000000-0000-4000-8000-000000000001", lineNumber: 1, materialId: "42000000-0000-4000-8000-000000000003", materialCode: "BR-6204", materialName: "深沟球轴承", materialSpecification: "6204-2RS", unit: "件", orderedQuantity: 600, receivedQuantity: 0, outstandingQuantity: 600, unitPrice: 80, netAmount: 48000, taxAmount: 6240, grossAmount: 54240 }],
};

describe("procurement and planning contracts", () => {
  it("accepts released purchase orders with explicit outstanding quantity", () => {
    expect(purchaseOrderRecordSchema.parse(order).lines[0].outstandingQuantity).toBe(600);
    expect(purchaseOrderRecordSchema.safeParse({ ...order, status: "CONFIRMED" }).success).toBe(false);
  });
  it("accepts controlled lead time and immutable receipt snapshots", () => {
    expect(materialPlanningParameterSchema.parse({ materialId: order.lines[0].materialId, materialCode: "BR-6204", materialName: "深沟球轴承", materialSpecification: "6204-2RS", procurementType: "BUY", unit: "件", leadTimeDays: 12, configured: true, version: 0, updatedAt: "2026-08-15T02:00:00Z" }).leadTimeDays).toBe(12);
    expect(mrpScheduledReceiptSnapshotSchema.parse({ id: crypto.randomUUID(), sourceType: "PURCHASE_ORDER", sourceOrderId: order.id, sourceOrderNumber: order.orderNumber, sourceLineId: order.lines[0].id, sourceName: order.supplierName, materialId: order.lines[0].materialId, materialCode: "BR-6204", materialName: "深沟球轴承", unit: "件", outstandingQuantity: 600, expectedReceiptDate: "2026-08-20", snapshottedAt: "2026-08-15T02:10:00Z" }).sourceType).toBe("PURCHASE_ORDER");
  });
});
