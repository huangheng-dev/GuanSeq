export const backendPageDataKeyByPath = {
  "/sales/customers/list": "masterData",
  "/product/materials/list": "masterData",
  "/procurement/suppliers/list": "masterData",
  "/sales/orders/list": "salesOrder",
  "/sales/deliveries/pending": "salesShipment",
  "/planning/demand/independent": "planningDemand",
  "/planning/mrp/runs": "mrpRun",
  "/planning/mrp/recommendations": "mrpSuggestion",
  "/planning/parameters": "planningParameter",
  "/product/boms/list": "bom",
  "/product/routings/list": "routing",
  "/warehouse/inventory/on-hand": "inventory",
  "/warehouse/staging": "materialIssue",
  "/warehouse/material-issues": "materialIssue",
  "/procurement/orders": "procurementOrder",
  "/procurement/receipts": "purchaseReceipt",
  "/production/orders/list": "productionOrder",
  "/production/work-orders/operations": "operationTask",
  "/production/reporting/reports": "productionExecution",
  "/quality/final": "finalInspection",
  "/quality/incoming": "incomingInspection",
  "/finance/order-profit": "orderProfit",
  "/finance/receivables": "receivable",
  "/finance/sales-settlement/invoicing": "receivable",
  "/finance/sales-settlement/receipts": "receivable",
  "/finance/payables": "payable",
  "/finance/purchase-settlement/invoices": "payable",
  "/finance/purchase-settlement/payments": "payable",
  "/finance/accounting-periods": "accountingPeriod",
  "/finance/purchase-settlement/grir-accruals": "grirAccrual",
  "/finance/advances": "advance",
  "/equipment/assets": "equipmentAsset",
  "/equipment/status": "equipmentAsset",
} as const;

export type BackendPageDataKey = (typeof backendPageDataKeyByPath)[keyof typeof backendPageDataKeyByPath];

export function getBackendPageDataKey(pathname: string): BackendPageDataKey | null {
  return backendPageDataKeyByPath[pathname as keyof typeof backendPageDataKeyByPath] ?? null;
}

export function isBackendPageUnavailable(pathname: string, loadedData: Partial<Record<BackendPageDataKey, unknown>>): boolean {
  const key = getBackendPageDataKey(pathname);
  if (!key) return false;
  const value = loadedData[key];
  if (value == null) return true;
  return typeof value === "object" && "source" in value && value.source === "unavailable";
}
