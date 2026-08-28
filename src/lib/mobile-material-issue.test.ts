import { describe, expect, it } from "vitest";
import type { MaterialIssueRecord } from "./contracts";
import { resolveIssueComponentScan, resolveIssueStockScan, resolveMaterialIssueScan } from "./mobile-material-issue";

const issue = {
  id: "11000000-0000-4000-8000-000000000001", issueNumber: "PI-20260827-000001", status: "DRAFT",
  warehouseId: "12000000-0000-4000-8000-000000000001",
  lines: [{ id: "13000000-0000-4000-8000-000000000001", componentMaterialId: "14000000-0000-4000-8000-000000000001", componentMaterialCode: "RM-STEEL", issuableQuantity: 8 }],
} as unknown as MaterialIssueRecord;
const stocks = [{
  id: "15000000-0000-4000-8000-000000000001", warehouseId: "12000000-0000-4000-8000-000000000001", warehouseCode: "WH-01",
  locationId: "16000000-0000-4000-8000-000000000001", locationCode: "A-01", locationName: "原料一位",
  materialId: "14000000-0000-4000-8000-000000000001", materialCode: "RM-STEEL", lotNumber: "LOT-2401", availableQuantity: 6, version: 3,
}];

describe("mobile material issue scan resolution", () => {
  it("resolves issue, component and exact stock labels", () => {
    const resolvedIssue = resolveMaterialIssueScan([issue], "PI:pi-20260827-000001").issue!;
    const line = resolveIssueComponentScan(resolvedIssue, "MAT|rm-steel").line!;
    expect(resolveIssueStockScan(stocks, resolvedIssue, line, `STOCK:${stocks[0].id}`).stock?.version).toBe(3);
  });

  it("fails closed for mismatches and ambiguous lots", () => {
    expect(resolveMaterialIssueScan([issue], "PI:missing").error).toContain("未找到");
    expect(resolveIssueComponentScan(issue, "MAT:unknown").error).toContain("不属于");
    expect(resolveIssueStockScan([...stocks, { ...stocks[0], id: "15000000-0000-4000-8000-000000000002", locationCode: "A-02" }], issue, issue.lines[0], "LOT:lot-2401").error).toContain("多个库存位置");
  });
});
