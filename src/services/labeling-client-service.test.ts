import { afterEach, describe, expect, it, vi } from "vitest";
import { submitLabelPrintRequest } from "./labeling-client-service";

const record = { id: "10000000-0000-4000-8000-000000000001", requestNumber: "LPR-20260827-000001",
  objectType: "OPERATION_TASK", objectId: "93000000-0000-4000-8000-000000000001", objectVersion: 2,
  objectCode: "OT-260815-900001", objectName: "机械装配", objectDetail: "MO-260815-012 · WC-ASM-01 · COMPLETED",
  payload: "OT:OT-260815-900001", templateCode: "OT", templateVersion: "OT-V1", mode: "INITIAL", copies: 1,
  reason: null, status: "PREPARED", actorUsername: "lin.hao", requestId: "label-request-1", preparedAt: "2026-08-27T11:00:00Z" };

afterEach(() => vi.restoreAllMocks());

describe("labeling client service", () => {
  it("preserves the stable request id and exact object version", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify(record), { status: 200 }));
    await expect(submitLabelPrintRequest({ objectType: "OPERATION_TASK", objectId: record.objectId,
      expectedObjectVersion: 2, mode: "INITIAL", copies: 1, reason: null }, "label-request-1")).resolves.toEqual(record);
    expect(fetchMock).toHaveBeenCalledWith("/api/labeling", expect.objectContaining({ headers: expect.objectContaining({ "X-Request-Id": "label-request-1" }) }));
    expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body))).toMatchObject({ expectedObjectVersion: 2, mode: "INITIAL" });
  });

  it("surfaces the backend conflict without inventing a successful print", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ message: "该对象已经生成过标签" }), { status: 409 }));
    await expect(submitLabelPrintRequest({ objectType: "OPERATION_TASK", objectId: record.objectId,
      expectedObjectVersion: 2, mode: "INITIAL", copies: 1, reason: null }, "label-request-2")).rejects.toThrow("已经生成过标签");
  });
});

