import { describe, expect, it } from "vitest";

import { salesOrderRecordSchema, salesOrderReferenceDataSchema } from "./contracts";

const order = {
  id: "51000000-0000-4000-8000-000000000001",
  orderNumber: "SO-260815-001",
  customerId: "41000000-0000-4000-8000-000000000001",
  customerCode: "CUS-0001",
  customerName: "恒锐自动化",
  currency: "CNY",
  taxRate: 0.13,
  requestedDeliveryDate: "2026-08-20",
  promisedDeliveryDate: "2026-08-19",
  owner: "沈妍",
  status: "DRAFT",
  totalNetAmount: 720000,
  totalTaxAmount: 93600,
  totalGrossAmount: 813600,
  rejectionReason: null,
  version: 0,
  updatedAt: "2026-08-15T00:00:00Z",
  lines: [{
    id: "52000000-0000-4000-8000-000000000001",
    lineNumber: 1,
    materialId: "42000000-0000-4000-8000-000000000001",
    materialCode: "GS-800",
    materialName: "伺服驱动控制柜",
    materialSpecification: "GS-800 标准型",
    unit: "台",
    quantity: 24,
    unitPrice: 30000,
    netAmount: 720000,
    taxAmount: 93600,
    grossAmount: 813600, deliveredQuantity: 0,
  }],
};

describe("sales order API contracts", () => {
  it("accepts a versioned order with material lines", () => {
    expect(salesOrderRecordSchema.parse(order).lines[0].materialCode).toBe("GS-800");
  });

  it("rejects empty order lines and invalid workflow status", () => {
    expect(salesOrderRecordSchema.safeParse({ ...order, lines: [] }).success).toBe(false);
    expect(salesOrderRecordSchema.safeParse({ ...order, status: "EXECUTING" }).success).toBe(false);
  });

  it("requires UUID-scoped active reference options", () => {
    expect(salesOrderReferenceDataSchema.safeParse({
      customers: [{ id: order.customerId, code: "CUS-0001", name: "恒锐自动化", creditLevel: "A" }],
      materials: [{ id: order.lines[0].materialId, code: "GS-800", name: "伺服驱动控制柜", specification: null, baseUnit: "台" }],
    }).success).toBe(true);
  });
});

