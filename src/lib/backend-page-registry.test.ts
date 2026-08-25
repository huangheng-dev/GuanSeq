import { describe, expect, it } from "vitest";

import { backendPageDataKeyByPath, getBackendPageDataKey, isBackendPageUnavailable } from "./backend-page-registry";

describe("backend page registry", () => {
  it("keeps every formal backend route unique and explicitly registered", () => {
    expect(Object.keys(backendPageDataKeyByPath)).toHaveLength(33);
    expect(getBackendPageDataKey("/sales/orders/list")).toBe("salesOrder");
    expect(getBackendPageDataKey("/finance/advances")).toBe("advance");
    expect(getBackendPageDataKey("/equipment/assets")).toBe("equipmentAsset");
    expect(getBackendPageDataKey("/equipment/status")).toBe("equipmentAsset");
  });

  it("fails closed when formal data is absent or marked unavailable", () => {
    expect(isBackendPageUnavailable("/sales/orders/list", {})).toBe(true);
    expect(isBackendPageUnavailable("/sales/orders/list", { salesOrder: { source: "backend" } })).toBe(false);
    expect(isBackendPageUnavailable("/warehouse/inventory/on-hand", { inventory: { source: "unavailable" } })).toBe(true);
    expect(isBackendPageUnavailable("/equipment/assets", {})).toBe(true);
    expect(isBackendPageUnavailable("/equipment/status", { equipmentAsset: { source: "backend" } })).toBe(false);
  });
});
