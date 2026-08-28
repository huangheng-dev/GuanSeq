import { describe, expect, it } from "vitest";

import { backendPageDataKeyByPath, getBackendPageDataKey, isBackendPageUnavailable } from "./backend-page-registry";

describe("backend page registry", () => {
  it("keeps every formal backend route unique and explicitly registered", () => {
    expect(Object.keys(backendPageDataKeyByPath)).toHaveLength(50);
    expect(getBackendPageDataKey("/sales/orders/list")).toBe("salesOrder");
    expect(getBackendPageDataKey("/sales/returns")).toBe("salesReturn");
    expect(getBackendPageDataKey("/procurement/returns")).toBe("purchaseReturn");
    expect(getBackendPageDataKey("/procurement/mobile-receiving")).toBe("purchaseReceipt");
    expect(getBackendPageDataKey("/production/mobile-operations/material-scan")).toBe("materialIssue");
    expect(getBackendPageDataKey("/production/mobile-operations/reporting-scan")).toBe("mobileReporting");
    expect(getBackendPageDataKey("/production/mobile-operations/label-reprint")).toBe("labeling");
    expect(getBackendPageDataKey("/warehouse/receiving")).toBe("putaway");
    expect(getBackendPageDataKey("/warehouse/barcodes/scanning")).toBe("putaway");
    expect(getBackendPageDataKey("/warehouse/inventory-operations/transfers")).toBe("inventoryControl");
    expect(getBackendPageDataKey("/warehouse/counts")).toBe("inventoryControl");
    expect(getBackendPageDataKey("/finance/advances")).toBe("advance");
    expect(getBackendPageDataKey("/equipment/assets")).toBe("equipmentAsset");
    expect(getBackendPageDataKey("/equipment/status")).toBe("equipmentAsset");
    expect(getBackendPageDataKey("/equipment/inspections")).toBe("equipmentWorkOrder");
    expect(getBackendPageDataKey("/equipment/maintenance")).toBe("equipmentWorkOrder");
    expect(getBackendPageDataKey("/equipment/work-orders")).toBe("equipmentWorkOrder");
    expect(getBackendPageDataKey("/equipment/spare-parts")).toBe("equipmentSparePart");
    expect(getBackendPageDataKey("/equipment/alerts")).toBe("equipmentAlert");
    expect(getBackendPageDataKey("/equipment/telemetry")).toBe("equipmentTelemetry");
    expect(getBackendPageDataKey("/equipment/oee")).toBe("equipmentOee");
  });

  it("fails closed when formal data is absent or marked unavailable", () => {
    expect(isBackendPageUnavailable("/sales/orders/list", {})).toBe(true);
    expect(isBackendPageUnavailable("/sales/orders/list", { salesOrder: { source: "backend" } })).toBe(false);
    expect(isBackendPageUnavailable("/warehouse/inventory/on-hand", { inventory: { source: "unavailable" } })).toBe(true);
    expect(isBackendPageUnavailable("/equipment/assets", {})).toBe(true);
    expect(isBackendPageUnavailable("/equipment/status", { equipmentAsset: { source: "backend" } })).toBe(false);
    expect(isBackendPageUnavailable("/equipment/maintenance", { equipmentWorkOrder: { source: "unavailable" } })).toBe(true);
    expect(isBackendPageUnavailable("/equipment/spare-parts", { equipmentSparePart: { source: "backend" } })).toBe(false);
    expect(isBackendPageUnavailable("/equipment/alerts", { equipmentAlert: { source: "unavailable" } })).toBe(true);
    expect(isBackendPageUnavailable("/equipment/telemetry", { equipmentTelemetry: { source: "unavailable" } })).toBe(true);
    expect(isBackendPageUnavailable("/equipment/oee", { equipmentOee: { source: "unavailable" } })).toBe(true);
  });
});
