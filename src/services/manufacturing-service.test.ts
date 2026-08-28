import { describe, expect, it } from "vitest";

import { allProductPaths, resolveProductRoute } from "@/lib/product-navigation";
import {
  getBusinessPage,
  getGlobalSearchIndex,
  getManufacturingSnapshot,
  submitBusinessMutation,
} from "./manufacturing-service";

describe("manufacturing service", () => {
  it("returns a contract-valid manufacturing snapshot", async () => {
    const result = await getManufacturingSnapshot();
    expect(result.flow).toHaveLength(6);
    expect(result.workOrders.some((order) => order.status === "有风险")).toBe(true);
    expect(result.capacity.every((item) => item.load >= 0 && item.load <= 100)).toBe(true);
  });

  it("keeps every global-search target inside the product navigation", async () => {
    const items = await getGlobalSearchIndex();

    expect(items.length).toBeGreaterThan(0);
    for (const item of items) expect(resolveProductRoute(item.href), item.href).not.toBeNull();
  });

  it("builds distinct page layouts with enough records for pagination", async () => {
    const planning = await getBusinessPage("/planning/mrp/runs");
    const inventory = await getBusinessPage("/warehouse/sales-shipping/picking");
    const analytics = await getBusinessPage("/analytics/operations");

    expect([planning?.layout, inventory?.layout, analytics?.layout]).toEqual(["planning", "inventory", "analytics"]);
    expect(new Set([planning?.context.kicker, inventory?.context.kicker, analytics?.context.kicker]).size).toBe(3);
    expect(planning?.rows.length).toBeGreaterThan(20);
    expect(inventory?.rows.length).toBeGreaterThan(20);
  });

  it("uses page-specific fields for audited core workflows", async () => {
    const sales = await getBusinessPage("/sales/orders/list");
    const profit = await getBusinessPage("/finance/order-profit");
    const roles = await getBusinessPage("/settings/roles");

    expect(sales?.columns).toEqual(["订单号", "客户 / 产品", "金额 / 币种", "要求 / 承诺交期", "履约进度"]);
    expect(profit?.context.title).toBe("订单利润构成");
    expect(roles?.cellFields).toEqual(["roleName", "members", "scope", "review"]);
  });

  it("accepts specialized forms without a generic name field", async () => {
    const result = await submitBusinessMutation({
      pathname: "/sales/orders/list",
      action: "create",
      values: { subject: "恒锐自动化 · GS-800", amount: "¥864,000 · CNY" },
    });

    expect(result.requestId).toMatch(/^REQ-/);
  });

  it("covers every declared route with a professional page definition", async () => {
    const pages = await Promise.all(allProductPaths().map((pathname) => getBusinessPage(pathname)));

    expect(pages).toHaveLength(236);
    expect(pages.every(Boolean)).toBe(true);
    expect(pages.every((page) => page?.definitionId !== "legacy-generic")).toBe(true);
    expect(new Set(pages.map((page) => page?.definitionId)).size).toBeGreaterThanOrEqual(20);
    for (const page of pages) {
      expect(page?.columns).toHaveLength(5);
      expect(page?.cellFields).toHaveLength(4);
      expect(page?.formFields.length).toBeGreaterThanOrEqual(6);
      expect(page?.rows.length).toBeGreaterThan(20);
      expect(page?.rows.every((row) => row.cells.length === 4)).toBe(true);
      expect(page?.rows.some((row) => row.cells.some((cell) => cell.includes(`${page.title} · 标准`)))).toBe(false);
    }
  });

  it("assigns primary actions by page purpose", async () => {
    const dashboard = await getBusinessPage("/");
    const analytics = await getBusinessPage("/analytics/inventory");
    const integration = await getBusinessPage("/settings/integrations/logs");
    const contract = await getBusinessPage("/sales/contracts");
    const traceability = await getBusinessPage("/production/traceability/lots");
    const audit = await getBusinessPage("/settings/audit");

    expect(dashboard?.primaryActionMode).toBe("refresh");
    expect(analytics?.primaryActionMode).toBe("refresh");
    expect(integration?.primaryActionMode).toBe("feedback");
    expect(contract?.primaryActionMode).toBe("form");
    expect(traceability?.primaryActionMode).toBe("query");
    expect(audit?.primaryActionMode).toBe("export");
  });

  it("describes the replaceable telemetry path without claiming field acceptance", async () => {
    const telemetry = await getBusinessPage("/equipment/telemetry");

    expect(telemetry?.planned).toBe(false);
    expect(telemetry?.primaryAction).toBe("接入设备");
    expect(telemetry?.context.summary).toContain("只替换端点和点位配置");
    expect(telemetry?.context.items.map((item) => item.value)).toEqual(["真实 API", "只读", "已建模", "待完成"]);
    expect(telemetry?.attentionTitle).toBe("设备采集真实边界");
    expect(JSON.stringify(telemetry)).not.toMatch(/46 台|684|91\.3%|主轴负载 68%/);
  });
});
