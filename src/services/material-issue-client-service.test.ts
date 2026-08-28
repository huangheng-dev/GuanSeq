import { afterEach, describe, expect, it, vi } from "vitest";
import { submitMaterialIssueAction } from "./material-issue-client-service";

describe("material issue mobile client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("keeps the stable request id and exact stock concurrency fields", async () => {
    const issue = { id: "61000000-0000-4000-8000-000000000001", issueNumber: "PI-20260827-000001" };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ issue }), {
      status: 200, headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(submitMaterialIssueAction({
      id: issue.id, action: "ISSUE", expectedVersion: 2, source: "MOBILE_SCAN",
      lines: [{ lineId: "62000000-0000-4000-8000-000000000001", quantity: 1, expectedLineVersion: 3,
        stockBalanceId: "73000000-0000-4000-8000-000000000001", expectedStockVersion: 4 }],
    }, "mobile-material-issue-stable-001")).resolves.toMatchObject(issue);
    expect(fetchMock).toHaveBeenCalledWith("/api/production/material-issues/mutate", expect.objectContaining({
      headers: expect.objectContaining({ "X-Request-Id": "mobile-material-issue-stable-001" }),
      body: expect.stringContaining('"source":"MOBILE_SCAN"'),
    }));
    expect(fetchMock.mock.calls[0]?.[1]?.body).toContain('"expectedStockVersion":4');
  });

  it("surfaces backend concurrency messages without discarding the draft", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "扫码库存已被其他用户修改，请重新扫描" }), {
      status: 409, headers: { "Content-Type": "application/json" },
    })));
    await expect(submitMaterialIssueAction({ id: "61000000-0000-4000-8000-000000000001", action: "ISSUE", expectedVersion: 2 },
      "mobile-material-issue-stable-002")).rejects.toThrow("请重新扫描");
  });
});
