import { describe, expect, it } from "vitest";
import { receivableInvoicePageSchema, receivableReferenceDataSchema } from "./contracts";

const ids = {
  invoice: "10000000-0000-4000-8000-000000000001",
  order: "10000000-0000-4000-8000-000000000002",
  customer: "10000000-0000-4000-8000-000000000003",
  line: "10000000-0000-4000-8000-000000000004",
  material: "10000000-0000-4000-8000-000000000005",
  invoiceLine: "10000000-0000-4000-8000-000000000006",
};

describe("销售应收契约", () => {
  it("解析应收发票、金额和核销状态", () => {
    const page = receivableInvoicePageSchema.parse({
      items: [{ id: ids.invoice, invoiceNumber: "ARINV-20260820-000001", salesOrderId: ids.order, orderNumber: "SO-001", customerId: ids.customer, customerCode: "C-001", customerName: "华东客户", currency: "CNY", invoiceDate: "2026-08-20", dueDate: "2026-09-19", taxRate: 0.13, netAmount: 100, taxAmount: 13, grossAmount: 113, receivedAmount: 0, outstandingAmount: 113, creditBalance: 0, status: "OPEN", version: 0, createdAt: "2026-08-20T01:00:00Z", lines: [{ id: ids.invoiceLine, salesOrderLineId: ids.line, lineNumber: 1, materialId: ids.material, materialCode: "FG-001", materialName: "成品", materialSpecification: null, unit: "台", invoiceQuantity: 1, unitPrice: 100, netAmount: 100, taxAmount: 13, grossAmount: 113 }], receipts: [] }],
      totalElements: 1, page: 0, size: 100, totalPages: 1,
    });
    expect(page.items[0].outstandingAmount).toBe(113);
  });

  it("解析订单行剩余可开票数量", () => {
    const data = receivableReferenceDataSchema.parse({ orders: [{ salesOrderId: ids.order, orderNumber: "SO-001", customerId: ids.customer, customerCode: "C-001", customerName: "华东客户", currency: "CNY", taxRate: 0.13, orderStatus: "PARTIALLY_SHIPPED", deliveredAmount: 200, invoicedAmount: 100, remainingAmount: 100, lines: [{ salesOrderLineId: ids.line, lineNumber: 1, materialId: ids.material, materialCode: "FG-001", materialName: "成品", materialSpecification: null, unit: "台", deliveredQuantity: 2, invoicedQuantity: 1, remainingQuantity: 1, unitPrice: 100 }] }] });
    expect(data.orders[0].lines[0].remainingQuantity).toBe(1);
  });

  it.each(["PARTIALLY_RETURNED", "RETURNED"] as const)("解析退货后的 %s 应收引用订单", (orderStatus) => {
    const data = receivableReferenceDataSchema.parse({ orders: [{ salesOrderId: ids.order, orderNumber: "SO-001", customerId: ids.customer, customerCode: "C-001", customerName: "华东客户", currency: "CNY", taxRate: 0.13, orderStatus, deliveredAmount: 100, invoicedAmount: 100, remainingAmount: 0, lines: [{ salesOrderLineId: ids.line, lineNumber: 1, materialId: ids.material, materialCode: "FG-001", materialName: "成品", materialSpecification: null, unit: "台", deliveredQuantity: 1, invoicedQuantity: 1, remainingQuantity: 0, unitPrice: 100 }] }] });
    expect(data.orders[0].orderStatus).toBe(orderStatus);
  });
});
