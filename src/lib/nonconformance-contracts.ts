import { z } from "zod";

export const nonconformanceStatusSchema = z.enum(["OPEN", "REVIEWED", "ACTION_REQUIRED", "ACTION_IN_PROGRESS", "VERIFICATION_PENDING", "CLOSED"]);
export const nonconformanceSeveritySchema = z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]);
export const nonconformanceSourceSchema = z.enum(["INCOMING_INSPECTION", "FINAL_INSPECTION"]);
export const nonconformanceDispositionSchema = z.enum(["RETURN_TO_SUPPLIER", "REWORK", "SCRAP", "CONCESSION", "SORTING", "OTHER"]);

export const nonconformanceEventSchema = z.object({
  id: z.string().uuid(), action: z.enum(["CREATED", "REVIEW", "DISPOSE", "PLAN_ACTION", "COMPLETE_ACTION", "VERIFY", "REOPEN"]),
  fromStatus: z.string().nullable(), toStatus: z.string(), actorUserId: z.string().uuid(), actorUsername: z.string(),
  reason: z.string().nullable(), requestId: z.string(), details: z.record(z.string(), z.unknown()), occurredAt: z.string().datetime(),
});

export const nonconformanceSchema = z.object({
  id: z.string().uuid(), caseNumber: z.string(), sourceType: nonconformanceSourceSchema,
  inspectionId: z.string().uuid(), inspectionNumber: z.string(), sourceDocumentId: z.string().uuid(), sourceDocumentNumber: z.string(),
  orderId: z.string().uuid(), orderNumber: z.string(), supplierId: z.string().uuid().nullable(), supplierCode: z.string().nullable(), supplierName: z.string().nullable(),
  materialId: z.string().uuid(), materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), unit: z.string(),
  nonconformingQuantity: z.number().positive(), defectDescription: z.string(), status: nonconformanceStatusSchema,
  severity: nonconformanceSeveritySchema.nullable(), immediateContainment: z.string().nullable(), reviewConclusion: z.string().nullable(), capaRequired: z.boolean().nullable(),
  dispositionType: nonconformanceDispositionSchema.nullable(), dispositionDecision: z.string().nullable(), dispositionEvidence: z.string().nullable(), dispositionOwner: z.string().nullable(),
  rootCause: z.string().nullable(), correctiveAction: z.string().nullable(), actionOwner: z.string().nullable(), actionDueDate: z.string().date().nullable(), overdue: z.boolean(),
  actionCompletionEvidence: z.string().nullable(), verificationEffective: z.boolean().nullable(), verificationConclusion: z.string().nullable(),
  version: z.number().int().nonnegative(), createdAt: z.string().datetime(), updatedAt: z.string().datetime(), closedAt: z.string().datetime().nullable(),
  events: z.array(nonconformanceEventSchema),
});

export const nonconformanceSummarySchema = z.object({
  open: z.number().int().nonnegative(), reviewed: z.number().int().nonnegative(), actionRequired: z.number().int().nonnegative(),
  actionInProgress: z.number().int().nonnegative(), verificationPending: z.number().int().nonnegative(), closed: z.number().int().nonnegative(), overdue: z.number().int().nonnegative(),
});

export const nonconformancePageSchema = z.object({
  items: z.array(nonconformanceSchema), totalElements: z.number().int().nonnegative(), page: z.number().int().nonnegative(),
  size: z.number().int().positive(), totalPages: z.number().int().nonnegative(), summary: nonconformanceSummarySchema,
  canReview: z.boolean(), canExecuteAction: z.boolean(), canVerify: z.boolean(),
});

const actionBase = { expectedVersion: z.number().int().nonnegative() };
export const nonconformanceActionSchema = z.discriminatedUnion("action", [
  z.object({ action: z.literal("REVIEW"), ...actionBase, severity: nonconformanceSeveritySchema,
    immediateContainment: z.string().trim().min(1).max(1000), reviewConclusion: z.string().trim().min(1).max(1000), capaRequired: z.boolean() }),
  z.object({ action: z.literal("DISPOSE"), ...actionBase, dispositionType: nonconformanceDispositionSchema,
    dispositionDecision: z.string().trim().min(1).max(1000), dispositionEvidence: z.string().trim().min(1).max(1000), dispositionOwner: z.string().trim().min(1).max(120) }),
  z.object({ action: z.literal("PLAN_ACTION"), ...actionBase, rootCause: z.string().trim().min(1).max(1000),
    correctiveAction: z.string().trim().min(1).max(1000), actionOwner: z.string().trim().min(1).max(120), actionDueDate: z.string().date() }),
  z.object({ action: z.literal("COMPLETE_ACTION"), ...actionBase, actionCompletionEvidence: z.string().trim().min(1).max(1000) }),
  z.object({ action: z.literal("VERIFY"), ...actionBase, effective: z.boolean(), verificationConclusion: z.string().trim().min(1).max(1000) }),
  z.object({ action: z.literal("REOPEN"), ...actionBase, reason: z.string().trim().min(4).max(1000) }),
]);

export type Nonconformance = z.infer<typeof nonconformanceSchema>;
export type NonconformancePage = z.infer<typeof nonconformancePageSchema>;
export type NonconformanceAction = z.infer<typeof nonconformanceActionSchema>;
export type NonconformanceView = "records" | "reviews" | "actions";
export type NonconformanceFilters = { query?: string; queue?: string; status?: string; severity?: string; sourceType?: string; overdue?: boolean; page?: number; size?: number };
