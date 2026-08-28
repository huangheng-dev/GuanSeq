import { afterEach, describe, expect, it, vi } from "vitest";
import { submitPurchaseReturn } from "./purchase-return-client-service";

const record = {
  id: "10000000-0000-4000-8000-000000000001", returnNumber: "PUR-20260827-000001",
  purchaseOrderId: "10000000-0000-4000-8000-000000000002", orderNumber: "PO-001",
  supplierId: "10000000-0000-4000-8000-000000000003", supplierCode: "S001", supplierName: "供应商甲",
  returnDate: "2026-08-27", status: "PENDING_SHIPMENT", reason: "来料质量异常退回", note: null,
  totalReturnQuantity: 1, acceptedReturnQuantity: 1, blockedReturnQuantity: 0, version: 0,
  createdAt: "2026-08-27T01:00:00Z", updatedAt: "2026-08-27T01:00:00Z", availableActions: ["CANCEL", "SHIP"], lines: [], events: [],
};

describe("purchase return client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("returns the persisted record", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ record }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(submitPurchaseReturn({ operation: "action", id: record.id, action: "CANCEL", expectedVersion: 0, reason: "供应商取消退回安排" })).resolves.toMatchObject({ returnNumber: record.returnNumber });
    expect(fetchMock).toHaveBeenCalledWith("/api/procurement/returns/mutate", expect.objectContaining({ method: "POST" }));
  });

  it("preserves the backend conflict message", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "采购退货单已被其他事务更新，请刷新后重试" }), { status: 409, headers: { "Content-Type": "application/json" } })));
    await expect(submitPurchaseReturn({ operation: "action", id: record.id, action: "SHIP", expectedVersion: 0, reason: "仓库核对批次后退回" })).rejects.toThrow("已被其他事务更新");
  });
});
