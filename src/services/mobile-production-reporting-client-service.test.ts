import { afterEach, describe, expect, it, vi } from "vitest";
import { submitMobileProductionReportingMutation } from "./mobile-production-reporting-client-service";

describe("mobile production reporting client service", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("keeps the stable request id and exact operator/task concurrency evidence", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "工序任务已被其他用户更新" }),
      { status: 409, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const requestId = "mobile-production-reporting-stable-001";
    await expect(submitMobileProductionReportingMutation({ kind: "TASK_ACTION",
      id: "81000000-0000-4000-8000-000000000001", action: "COMPLETE", expectedVersion: 3,
      shiftName: "白班", completedQuantity: 2, note: null, operatorBadge: "lin.hao" }, requestId))
      .rejects.toThrow("工序任务已被其他用户更新");
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)["X-Request-Id"]).toBe(requestId);
    expect(JSON.parse(String(init.body))).toMatchObject({ kind: "TASK_ACTION", expectedVersion: 3, operatorBadge: "lin.hao" });
  });

  it("sends the final operation id and order version for formal work reporting", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "生产订单已被其他用户更新" }),
      { status: 409, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(submitMobileProductionReportingMutation({ kind: "WORK_REPORT",
      orderId: "91000000-0000-4000-8000-000000000001", operationTaskId: "81000000-0000-4000-8000-000000000003",
      quantity: 2, shiftName: "白班", note: "移动报工", expectedOrderVersion: 2, operatorBadge: "lin.hao" },
    "mobile-production-reporting-stable-002")).rejects.toThrow("生产订单已被其他用户更新");
    const payload = JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body));
    expect(payload).toMatchObject({ kind: "WORK_REPORT", operationTaskId: "81000000-0000-4000-8000-000000000003",
      expectedOrderVersion: 2, operatorBadge: "lin.hao" });
  });
});
