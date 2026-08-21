import { describe, expect, it } from "vitest";

import { allProductPaths, resolveProductRoute } from "./product-navigation";

describe("product navigation routes", () => {
  it("resolves every declared navigation path", () => {
    const paths = allProductPaths();
    expect(new Set(paths).size).toBe(paths.length);
    expect(paths.length).toBeGreaterThan(90);
    for (const path of paths) expect(resolveProductRoute(path), path).not.toBeNull();
  });

  it("rejects unknown or over-deep paths", () => {
    expect(resolveProductRoute("/unknown")) .toBeNull();
    expect(resolveProductRoute("/production/orders/list/detail")) .toBeNull();
  });

  it("includes the audited order-to-cash and shop-floor capabilities", () => {
    expect(allProductPaths()).toEqual(expect.arrayContaining([
      "/my-work/todos",
      "/settings/master-data/plants",
      "/warehouse/sales-shipping/picking",
      "/warehouse/inventory-operations/transfers",
      "/warehouse/barcodes/scanning",
      "/production/exceptions/rework",
      "/quality/customer-quality/eight-d",
      "/finance/sales-settlement/invoicing",
      "/finance/purchase-settlement/payments",
      "/notifications",
      "/sales/pricing/discounts",
      "/procurement/sourcing/comparisons",
      "/planning/demand/independent",
      "/production/work-orders/execution",
      "/warehouse/lot-serial",
      "/quality/plans",
      "/equipment/oee",
      "/finance/cost-variances",
      "/analytics/inventory",
      "/settings/ai/governance",
      "/settings/integrations/logs",
    ]));
  });
});
