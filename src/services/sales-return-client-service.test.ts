import { afterEach, describe, expect, it, vi } from "vitest";
import { submitSalesReturn } from "./sales-return-client-service";

const record = {
  id: "10000000-0000-4000-8000-000000000001", returnNumber: "SR-20260827-000001",
  salesOrderId: "10000000-0000-4000-8000-000000000002", orderNumber: "SO-001",
  customerId: "10000000-0000-4000-8000-000000000003", customerCode: "C001", customerName: "客户甲",
  returnDate: "2026-08-27", status: "PENDING_RECEIPT", reason: "客户申请质量退货", note: null,
  warehouseId: null, warehouseCode: null, warehouseName: null, locationId: null, locationCode: null, locationName: null,
  totalReturnQuantity: 1, receivedAt: null, inspectedAt: null, version: 0,
  createdAt: "2026-08-27T01:00:00Z", updatedAt: "2026-08-27T01:00:00Z", availableActions: ["CANCEL", "RECEIVE"],
  lines: [], events: [],
};

describe("sales return client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("returns the persisted record", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ record }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(submitSalesReturn({ operation: "action", id: record.id, action: "CANCEL", expectedVersion: 0, reason: "客户撤回退货申请" })).resolves.toMatchObject({ returnNumber: record.returnNumber });
    expect(fetchMock).toHaveBeenCalledWith("/api/sales/returns/mutate", expect.objectContaining({ method: "POST" }));
  });

  it("preserves the backend conflict message", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "销售退货单已经被其他用户更新，请刷新后重试" }), { status: 409, headers: { "Content-Type": "application/json" } })));
    await expect(submitSalesReturn({ operation: "action", id: record.id, action: "CANCEL", expectedVersion: 0, reason: "客户撤回退货申请" })).rejects.toThrow("已经被其他用户更新");
  });
});
