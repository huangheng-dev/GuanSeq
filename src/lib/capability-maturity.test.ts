import { describe, expect, it } from "vitest";

import { allProductPaths } from "./product-navigation";
import { getCapabilityMaturity, getCapabilityMaturitySummary } from "./capability-maturity";

describe("product capability maturity", () => {
  it("classifies every declared product path into exactly one maturity state", () => {
    const paths = allProductPaths();
    const maturities = paths.map(getCapabilityMaturity);

    expect(maturities).not.toContain(null);
    expect(getCapabilityMaturitySummary(paths)).toEqual({ backend: 57, mock: 167, planned: 12 });
  });

  it("keeps formal, mock and planned routes semantically distinct", () => {
    expect(getCapabilityMaturity("/sales/orders/list")).toBe("backend");
    expect(getCapabilityMaturity("/settings/organization/users")).toBe("backend");
    expect(getCapabilityMaturity("/settings/roles")).toBe("backend");
    expect(getCapabilityMaturity("/quality/nonconformance/actions")).toBe("backend");
    expect(getCapabilityMaturity("/sales/orders/approvals")).toBe("mock");
    expect(getCapabilityMaturity("/settings/integrations/logs")).toBe("planned");
    expect(getCapabilityMaturity("/not-a-product-route")).toBeNull();
  });
});
