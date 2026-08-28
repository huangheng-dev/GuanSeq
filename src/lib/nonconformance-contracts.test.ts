import { describe, expect, it } from "vitest";
import { nonconformanceActionSchema, nonconformancePageSchema } from "./nonconformance-contracts";

describe("quality nonconformance contracts", () => {
  it("parses current-workspace page and immutable evidence", () => {
    const id = crypto.randomUUID();
    const page = nonconformancePageSchema.parse({ items: [{ id, caseNumber: "NCR-20260829-000001", sourceType: "FINAL_INSPECTION",
      inspectionId: crypto.randomUUID(), inspectionNumber: "FQI-1", sourceDocumentId: crypto.randomUUID(), sourceDocumentNumber: "RPT-1",
      orderId: crypto.randomUUID(), orderNumber: "MO-1", supplierId: null, supplierCode: null, supplierName: null,
      materialId: crypto.randomUUID(), materialCode: "GS-800", materialName: "控制柜", materialSpecification: null, unit: "台",
      nonconformingQuantity: 1, defectDescription: "扭矩不足", status: "OPEN", severity: null, immediateContainment: null,
      reviewConclusion: null, capaRequired: null, dispositionType: null, dispositionDecision: null, dispositionEvidence: null,
      dispositionOwner: null, rootCause: null, correctiveAction: null, actionOwner: null, actionDueDate: null, overdue: false,
      actionCompletionEvidence: null, verificationEffective: null, verificationConclusion: null, version: 0,
      createdAt: "2026-08-29T00:00:00Z", updatedAt: "2026-08-29T00:00:00Z", closedAt: null, events: [] }],
      totalElements: 1, page: 0, size: 20, totalPages: 1,
      summary: { open: 1, reviewed: 0, actionRequired: 0, actionInProgress: 0, verificationPending: 0, closed: 0, overdue: 0 },
      canReview: true, canExecuteAction: true, canVerify: true });
    expect(page.items[0].id).toBe(id);
  });

  it("rejects incomplete action evidence", () => {
    expect(nonconformanceActionSchema.safeParse({ action: "VERIFY", expectedVersion: 2, effective: true }).success).toBe(false);
  });
});
