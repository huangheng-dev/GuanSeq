import { describe, expect, it } from "vitest";

import { bomReferenceDataSchema, routingReferenceDataSchema } from "./contracts";

const material = {
  id: "11111111-1111-4111-8111-111111111111",
  code: "GS-800",
  name: "伺服驱动控制柜",
  specification: null,
  baseUnit: "台",
  procurementType: "MAKE" as const,
};

describe("产品与工艺参考数据契约", () => {
  it("按 OpenAPI 接受不含来料检验字段的 BOM 物料选项", () => {
    const result = bomReferenceDataSchema.parse({
      parentMaterials: [material],
      componentMaterials: [material],
    });

    expect(result.parentMaterials[0]?.code).toBe("GS-800");
  });

  it("工艺路线与 BOM 共用同一精简物料引用契约", () => {
    const result = routingReferenceDataSchema.parse({ materials: [material] });

    expect(result.materials[0]?.procurementType).toBe("MAKE");
  });
});
