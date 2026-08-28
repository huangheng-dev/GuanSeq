import { afterEach, describe, expect, it, vi } from "vitest";

import { submitCreatePurchaseReceipt } from "./purchase-receipt-client-service";

const payload = {
  purchaseOrderId: "82000000-0000-4000-8000-000000000001",
  warehouseId: "71000000-0000-4000-8000-000000000001",
  locationId: "72000000-0000-4000-8000-000000000001",
  source: "MOBILE_SCAN" as const,
  lines: [{
    orderLineId: "83000000-0000-4000-8000-000000000001",
    receivedQuantity: 2,
    lotNumber: "LOT-MOBILE-001",
  }],
};

describe("purchase receipt client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("reuses the stable request id needed by weak-network retries", async () => {
    const receipt = { id: "10000000-0000-4000-8000-000000000001", receiptNumber: "PR-20260827-000001", source: "MOBILE_SCAN" };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ receipt }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(submitCreatePurchaseReceipt(payload, "mobile-receipt-stable-001")).resolves.toMatchObject(receipt);
    expect(fetchMock).toHaveBeenCalledWith("/api/procurement/receipts/mutate", expect.objectContaining({
      headers: expect.objectContaining({ "X-Request-Id": "mobile-receipt-stable-001" }),
      body: expect.stringContaining('"source":"MOBILE_SCAN"'),
    }));
  });

  it("keeps the backend validation message for a retryable draft", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "本次收货数量不能超过未收数量 1" }), {
      status: 422,
      headers: { "Content-Type": "application/json" },
    })));
    await expect(submitCreatePurchaseReceipt(payload, "mobile-receipt-stable-002")).rejects.toThrow("不能超过未收数量");
  });
});
