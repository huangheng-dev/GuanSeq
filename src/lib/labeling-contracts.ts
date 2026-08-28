import { z } from "zod";

export const labelObjectTypeSchema = z.enum(["OPERATION_TASK", "EMPLOYEE", "STOCK_BALANCE"]);
export const labelModeSchema = z.enum(["INITIAL", "REPRINT"]);

export const labelPrintRequestSchema = z.object({
  id: z.string().uuid(), requestNumber: z.string(), objectType: labelObjectTypeSchema, objectId: z.string().uuid(),
  objectVersion: z.number().int().nonnegative(), objectCode: z.string(), objectName: z.string(), objectDetail: z.string(),
  payload: z.string(), templateCode: z.enum(["OT", "EMP", "STOCK"]), templateVersion: z.enum(["OT-V1", "EMP-V1", "STOCK-V1"]),
  mode: labelModeSchema, copies: z.number().int().min(1).max(10), reason: z.string().nullable(), status: z.literal("PREPARED"),
  actorUsername: z.string(), requestId: z.string(), preparedAt: z.string(),
});

export const labelReferenceDataSchema = z.object({
  allowedObjectTypes: z.array(labelObjectTypeSchema),
  templates: z.array(z.object({ objectType: labelObjectTypeSchema, code: z.enum(["OT", "EMP", "STOCK"]),
    name: z.string(), version: z.enum(["OT-V1", "EMP-V1", "STOCK-V1"]), paperSize: z.string() })),
  candidates: z.array(z.object({ objectType: labelObjectTypeSchema, objectId: z.string().uuid(), version: z.number().int().nonnegative(),
    code: z.string(), name: z.string(), detail: z.string(), payload: z.string(), hasPreparedRequest: z.boolean() })),
});

export const labelPrintRequestPageSchema = z.object({
  items: z.array(labelPrintRequestSchema), total: z.number().int().nonnegative(), page: z.number().int().nonnegative(),
  size: z.number().int().positive(), totalPages: z.number().int().nonnegative(),
});

export type LabelObjectType = z.infer<typeof labelObjectTypeSchema>;
export type LabelMode = z.infer<typeof labelModeSchema>;
export type LabelPrintRequest = z.infer<typeof labelPrintRequestSchema>;
export type LabelReferenceData = z.infer<typeof labelReferenceDataSchema>;
export type LabelCandidate = LabelReferenceData["candidates"][number];

