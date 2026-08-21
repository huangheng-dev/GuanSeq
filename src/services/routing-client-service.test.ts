import { afterEach, describe, expect, it, vi } from "vitest";

import { submitRoutingMutation } from "./routing-client-service";

afterEach(() => vi.unstubAllGlobals());

describe("submitRoutingMutation", () => {
  it("提交工艺路线并透传请求编号", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ routing: { id: "64000000-0000-4000-8000-000000000001" } }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("crypto", { randomUUID: () => "routing-request-1" });
    await submitRoutingMutation({
      operation: "create",
      payload: { materialId: "42000000-0000-4000-8000-000000000001", usageType: "PRODUCTION", versionCode: "V1", baseQuantity: 1, effectiveFrom: "2026-08-15", owner: "何工", changeReason: "测试", operations: [{ operationCode: "OP-10", operationName: "装配", workCenterCode: "WC-01", workCenterName: "装配中心", setupMinutes: 10, runMinutesPerUnit: 20, queueMinutes: 0, inspectionRequired: false }] },
    });
    expect(fetchMock).toHaveBeenCalledWith("/api/product/routings", expect.objectContaining({ headers: expect.objectContaining({ "X-Request-Id": "web-routing-routing-request-1" }) }));
  });

  it("保留后端冲突消息", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "该物料已有已发布工艺路线" }), { status: 409, headers: { "Content-Type": "application/json" } })));
    vi.stubGlobal("crypto", { randomUUID: () => "routing-request-2" });
    await expect(submitRoutingMutation({ operation: "action", id: "64000000-0000-4000-8000-000000000001", action: "PUBLISH", expectedVersion: 0 })).rejects.toThrow("该物料已有已发布工艺路线");
  });
});
