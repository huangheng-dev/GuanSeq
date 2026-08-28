import type { MaterialIssueLine, MaterialIssueRecord, MaterialIssueReferenceData } from "@/lib/contracts";

export type MobileIssueStock = MaterialIssueReferenceData["availableStocks"][number];

function canonical(value: string, prefix: "PI" | "MAT" | "STOCK" | "LOT") {
  return value.trim().replace(new RegExp(`^${prefix}[:|]`, "i"), "").trim();
}

export function resolveMaterialIssueScan(issues: MaterialIssueRecord[], scannedValue: string) {
  const value = canonical(scannedValue, "PI").toLocaleUpperCase("en-US");
  if (!value) return { issue: null, error: "请扫描或输入生产领料单号。" };
  const issue = issues.find((item) => ["DRAFT", "PARTIAL"].includes(item.status)
    && (item.issueNumber.toLocaleUpperCase("en-US") === value || item.id.toLocaleUpperCase("en-US") === value)) ?? null;
  return issue ? { issue, error: "" } : { issue: null, error: "未找到可发料的生产领料单，或领料单已完成/取消。" };
}

export function resolveIssueComponentScan(issue: MaterialIssueRecord, scannedValue: string) {
  const value = canonical(scannedValue, "MAT").toLocaleUpperCase("en-US");
  if (!value) return { line: null, error: "请扫描或输入组件物料编码。" };
  const matches = issue.lines.filter((line) => line.issuableQuantity > 0
    && (line.componentMaterialCode.toLocaleUpperCase("en-US") === value || line.id.toLocaleUpperCase("en-US") === value));
  return matches.length === 1
    ? { line: matches[0], error: "" }
    : { line: null, error: matches.length > 1 ? "该组件对应多行需求，请扫描领料行标签。" : "该组件不属于当前领料单，或需求已全部发料。" };
}

export function resolveIssueStockScan(
  stocks: MobileIssueStock[], issue: MaterialIssueRecord, line: MaterialIssueLine, scannedValue: string,
) {
  const raw = scannedValue.trim();
  if (!raw) return { stock: null, error: "请扫描库存标签或批次条码。" };
  const eligible = stocks.filter((stock) => stock.warehouseId === issue.warehouseId
    && stock.materialId === line.componentMaterialId && stock.availableQuantity > 0);
  const isStockTag = /^STOCK[:|]/i.test(raw);
  const value = canonical(raw, isStockTag ? "STOCK" : "LOT").toLocaleUpperCase("en-US");
  const matches = isStockTag || /^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(value)
    ? eligible.filter((stock) => stock.id.toLocaleUpperCase("en-US") === value)
    : eligible.filter((stock) => stock.lotNumber.toLocaleUpperCase("en-US") === value);
  if (matches.length === 1) return { stock: matches[0], error: "" };
  if (matches.length > 1) return { stock: null, error: "该批次存在多个库存位置，请扫描 STOCK 库存标签以精确选择。" };
  return { stock: null, error: "未找到与当前仓库、组件匹配的可用库存，或库存已被占用。" };
}
