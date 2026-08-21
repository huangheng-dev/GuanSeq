import { describe, expect, it } from "vitest";

import { independentDemandRecordSchema, planningDemandReferenceDataSchema } from "./contracts";

const demand = {
  id: "53000000-0000-4000-8000-000000000001",
  demandNumber: "DMD-260815-001",
  sourceType: "SALES_ORDER",
  sourceId: "51000000-0000-4000-8000-000000000004",
  sourceNumber: "SO-260815-004",
  sourceLineId: "52000000-0000-4000-8000-000000000004",
  sourceLineNumber: 1,
  sourceCustomer: "恒锐自动化",
  materialId: "42000000-0000-4000-8000-000000000001",
  materialCode: "GS-800",
  materialName: "伺服驱动控制柜",
  materialSpecification: "GS-800 标准型",
  unit: "台",
  quantity: 6,
  requiredDate: "2026-08-22",
  priority: "NORMAL",
  owner: "沈妍",
  status: "ACTIVE",
  note: "由销售订单下达自动生成",
  cancellationReason: null,
  version: 0,
  updatedAt: "2026-08-15T00:00:00Z",
};

describe("planning demand API contracts", () => {
  it("accepts a traceable sales-order demand", () => {
    expect(independentDemandRecordSchema.parse(demand).sourceNumber).toBe("SO-260815-004");
  });

  it("rejects invalid demand status and quantity", () => {
    expect(independentDemandRecordSchema.safeParse({ ...demand, status: "RELEASED" }).success).toBe(false);
    expect(independentDemandRecordSchema.safeParse({ ...demand, quantity: 0 }).success).toBe(false);
  });

  it("accepts active material reference options", () => {
    expect(planningDemandReferenceDataSchema.safeParse({ materials: [{
      id: demand.materialId,
      code: demand.materialCode,
      name: demand.materialName,
      specification: demand.materialSpecification,
      baseUnit: demand.unit,
    }] }).success).toBe(true);
  });
});
