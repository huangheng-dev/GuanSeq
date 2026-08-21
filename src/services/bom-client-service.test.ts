import { afterEach, describe, expect, it, vi } from "vitest";

import { submitBomMutation } from "./bom-client-service";

afterEach(() => vi.unstubAllGlobals());

describe("BOM client service", () => {
  it("sends a controlled draft mutation with a request id", async () => {
    const bom = { id: "61000000-0000-4000-8000-000000000001", status: "DRAFT" };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ bom })));
    vi.stubGlobal("fetch", fetchMock);
    const input = {
      operation: "create" as const,
      payload: {
        parentMaterialId: "42000000-0000-4000-8000-000000000001",
        usageType: "PRODUCTION" as const,
        versionCode: "V1.0",
        baseQuantity: 1,
        effectiveFrom: "2026-08-15",
        owner: "何工",
        changeReason: "首版结构",
        lines: [{ componentMaterialId: "42000000-0000-4000-8000-000000000003", quantity: 2, scrapRate: 0.01 }],
      },
    };

    await expect(submitBomMutation(input)).resolves.toEqual(bom);
    expect(fetchMock).toHaveBeenCalledWith("/api/product/boms", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({ "X-Request-Id": expect.stringMatching(/^web-bom-/) }),
      body: JSON.stringify(input),
    }));
  });

  it("keeps the backend conflict message", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "BOM 已被其他用户修改，请刷新后重试" }), { status: 409 })));
    await expect(submitBomMutation({ operation: "action", id: "61000000-0000-4000-8000-000000000001", action: "PUBLISH", expectedVersion: 0 }))
      .rejects.toThrow("BOM 已被其他用户修改，请刷新后重试");
  });
});
