import { expect, test, type Locator, type Page } from "@playwright/test";

async function openFormalPage(page: Page, path: string, heading: string) {
  const response = await page.goto(path);
  expect(response?.ok()).toBeTruthy();
  await expect(page.getByRole("heading", { name: heading, exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "能力成熟度" })).toContainText("正式后端");
}

async function activeDialog(page: Page, heading: string | RegExp) {
  const title = page.getByRole("heading", { name: heading });
  await expect(title).toBeVisible();
  const dialog = page.getByRole("dialog").filter({ has: title }).last();
  await expect(dialog).toBeVisible();
  return dialog;
}

async function submitAndClose(dialog: Locator, buttonName: string | RegExp) {
  await dialog.getByRole("button", { name: buttonName }).click();
  await expect(dialog).toBeHidden({ timeout: 60_000 });
}

test("R0 发行冒烟：初始化、登录、主闭环、退货与失败恢复", async ({ page }) => {
  await test.step("空库迁移后的开发身份登录入口可进入正式工作台", async () => {
    const health = await page.request.get("/api/health");
    expect(health.ok()).toBeTruthy();

    await page.goto("/login?returnTo=%2Fsales%2Forders%2Flist");
    await expect(page.getByRole("heading", { name: "进入贯序制造工作台" })).toBeVisible();
    await expect(page.getByText("当前为本地开发身份模式，可直接返回工作台。")).toBeVisible();
    await page.getByRole("link", { name: "企业身份登录" }).click();
    await expect(page).toHaveURL(/\/sales\/orders\/list$/);
    await expect(page.getByRole("heading", { name: "销售订单", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: /林浩 计划主管/ })).toBeVisible();
  });

  await test.step("采购到货、来料检验与供应商退货形成真实库存闭环", async () => {
    await openFormalPage(page, "/procurement/receipts", "采购到货协同");
    await page.getByRole("button", { name: "登记到货", exact: true }).click();
    let dialog = await activeDialog(page, "登记采购到货");
    await dialog.getByLabel("BR-6204收货数量").fill("1");
    await dialog.getByLabel("BR-6204批号").fill("E2E-BR-6204");
    await submitAndClose(dialog, "登记到货");
    await expect(page.getByRole("table", { name: "采购收货列表" })).toContainText("待检");

    await openFormalPage(page, "/quality/incoming", "来料检验 IQC");
    await page.getByRole("button", { name: "录入判定" }).click();
    dialog = await activeDialog(page, "来料检验判定");
    await expect(dialog.getByRole("spinbutton", { name: "合格数量", exact: true })).toHaveValue("1");
    await submitAndClose(dialog, "提交判定");
    await expect(page.getByRole("table", { name: "来料检验任务" })).toContainText("合格 1/0");

    await openFormalPage(page, "/procurement/returns", "采购退货与供应商处置");
    await page.getByRole("button", { name: "建立采购退货" }).click();
    dialog = await activeDialog(page, "建立采购退货");
    await dialog.getByLabel("责任原因").fill("E2E 来料复核后退回供应商");
    await dialog.getByLabel("BR-6204合格库存退货数量").fill("1");
    await submitAndClose(dialog, "确认退货授权");
    await expect(page.getByRole("table", { name: "采购退货列表" })).toContainText("待退回出库");

    await page.getByRole("button", { name: "退回出库" }).click();
    dialog = await activeDialog(page, /退回出库 ·/);
    await dialog.getByLabel("动作原因").fill("E2E 仓库复核后退回供应商");
    await dialog.getByRole("button", { name: "确认退回出库" }).click();
    await expect(page.getByRole("table", { name: "采购退货列表" })).toContainText("已退回供应商", { timeout: 60_000 });
  });

  await test.step("生产订单开工、报工、完工检验与成品入库闭环", async () => {
    await openFormalPage(page, "/production/orders/list", "生产订单");
    await page.getByRole("button", { name: "查看MO-260815-012详情" }).click();
    await page.getByRole("button", { name: "确认开工" }).click();
    let dialog = await activeDialog(page, "确认生产开工");
    await submitAndClose(dialog, "确认开工");
    await expect(page.getByRole("table", { name: "生产订单列表" })).toContainText("执行中");

    await openFormalPage(page, "/production/reporting/reports", "生产报工");
    await page.getByRole("button", { name: "提交报工" }).click();
    dialog = await activeDialog(page, "提交生产报工");
    await dialog.getByLabel("班次").fill("E2E 白班");
    await dialog.getByLabel("操作人").fill("林浩");
    await submitAndClose(dialog, "提交并送检");
    await expect(page.getByRole("table", { name: "生产报工列表" })).toContainText("待检验");

    await openFormalPage(page, "/quality/final", "完工检验");
    await page.getByRole("button", { name: /^检验/ }).first().click();
    dialog = await activeDialog(page, "提交完工检验");
    await submitAndClose(dialog, "确认判定");
    await expect(page.getByRole("table", { name: "完工检验列表" })).toContainText("已判定");

    await openFormalPage(page, "/production/reporting/reports", "生产报工");
    await page.getByRole("button", { name: /^查看.*详情$/ }).first().click();
    await page.getByRole("button", { name: "检验放行并入库" }).click();
    dialog = await activeDialog(page, "检验放行并入库");
    await submitAndClose(dialog, "确认入库");
    await expect(page.getByRole("table", { name: "生产报工列表" })).toContainText("已入库");
  });

  await test.step("销售发货与客户退货可在接口失败后无重复写入地恢复", async () => {
    await openFormalPage(page, "/sales/deliveries/pending", "待发货协同");
    await page.getByRole("button", { name: "登记发货", exact: true }).click();
    let dialog = await activeDialog(page, "登记销售发货");
    await dialog.getByLabel("GS-800发货数量").fill("1");
    await submitAndClose(dialog, "确认发货");
    await expect(page.getByRole("table", { name: "销售发货列表" })).toContainText("已发货");

    await openFormalPage(page, "/sales/returns", "销售退货与质量处置");
    await page.getByRole("button", { name: "建立退货授权" }).click();
    dialog = await activeDialog(page, "建立销售退货授权");
    await dialog.getByLabel("责任原因").fill("E2E 客户退回质量复核");
    await dialog.getByLabel("GS-800退货数量").fill("1");

    let failedOnce = false;
    await page.route("**/api/sales/returns/mutate", async (route) => {
      if (!failedOnce) {
        failedOnce = true;
        await route.abort("failed");
        return;
      }
      await route.continue();
    });
    await dialog.getByRole("button", { name: "确认授权" }).click();
    await expect(dialog.getByText(/Failed to fetch|fetch failed|销售退货动作失败/)).toBeVisible();
    await expect(page.getByRole("table", { name: "销售退货列表" })).toContainText("暂无销售退货记录");
    await page.unroute("**/api/sales/returns/mutate");

    await submitAndClose(dialog, "确认授权");
    await expect(page.getByRole("table", { name: "销售退货列表" })).toContainText("待收货");

    await page.getByRole("button", { name: "登记收货" }).click();
    dialog = await activeDialog(page, /登记收货 ·/);
    await dialog.getByLabel("GS-800批次").fill("E2E-SALES-RETURN");
    await dialog.getByLabel("动作原因").fill("E2E 仓库登记客户退货");
    await submitAndClose(dialog, "确认登记收货");
    await expect(page.getByRole("table", { name: "销售退货列表" })).toContainText("待质检");

    await page.getByRole("button", { name: "质量判定" }).click();
    dialog = await activeDialog(page, /质量判定 ·/);
    await dialog.getByLabel("动作原因").fill("E2E 质量复核合格回库");
    await submitAndClose(dialog, "确认质量判定");
    await expect(page.getByRole("table", { name: "销售退货列表" })).toContainText("已处置");
  });
});
