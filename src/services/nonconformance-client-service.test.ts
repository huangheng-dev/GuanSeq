import { afterEach, describe, expect, it, vi } from "vitest";
import { refreshNonconformances, submitNonconformanceAction } from "./nonconformance-client-service";

const page = { items: [], totalElements: 0, page: 0, size: 20, totalPages: 0,
  summary: { open: 0, reviewed: 0, actionRequired: 0, actionInProgress: 0, verificationPending: 0, closed: 0, overdue: 0 },
  canReview: true, canExecuteAction: true, canVerify: true };

describe("nonconformance client service", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("passes server-side queue filters through the BFF", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ page }), { status: 200 })); vi.stubGlobal("fetch", fetchMock);
    await refreshNonconformances("actions", { status: "ACTION_IN_PROGRESS", overdue: true, page: 1 });
    expect(fetchMock.mock.calls[0][0]).toContain("view=actions");
    expect(fetchMock.mock.calls[0][0]).toContain("overdue=true");
    expect(fetchMock.mock.calls[0][0]).toContain("page=1");
  });

  it("keeps the stable request id on a write", async () => {
    const id = crypto.randomUUID(); const item = { id, caseNumber: "NCR-1", sourceType: "FINAL_INSPECTION", inspectionId: crypto.randomUUID(), inspectionNumber: "FQI-1",
      sourceDocumentId: crypto.randomUUID(), sourceDocumentNumber: "RPT-1", orderId: crypto.randomUUID(), orderNumber: "MO-1", supplierId: null, supplierCode: null, supplierName: null,
      materialId: crypto.randomUUID(), materialCode: "GS-800", materialName: "控制柜", materialSpecification: null, unit: "台", nonconformingQuantity: 1,
      defectDescription: "划伤", status: "REVIEWED", severity: "LOW", immediateContainment: "隔离", reviewConclusion: "偶发", capaRequired: false,
      dispositionType: null, dispositionDecision: null, dispositionEvidence: null, dispositionOwner: null, rootCause: null, correctiveAction: null, actionOwner: null,
      actionDueDate: null, overdue: false, actionCompletionEvidence: null, verificationEffective: null, verificationConclusion: null,
      version: 1, createdAt: "2026-08-29T00:00:00Z", updatedAt: "2026-08-29T00:01:00Z", closedAt: null, events: [] };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(item), { status: 200 })); vi.stubGlobal("fetch", fetchMock);
    await submitNonconformanceAction(id, { action: "REVIEW", expectedVersion: 0, severity: "LOW", immediateContainment: "隔离", reviewConclusion: "偶发", capaRequired: false }, "nc-stable-1");
    expect((fetchMock.mock.calls[0][1] as RequestInit).headers).toMatchObject({ "X-Request-Id": "nc-stable-1" });
  });
});
