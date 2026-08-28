import { z } from "zod";

export const putawayStatusSchema = z.enum(["OPEN", "COMPLETED", "CANCELLED", "REVERSED"]);

export const putawayTaskSchema = z.object({
  id: z.string().uuid(), taskNumber: z.string(), status: putawayStatusSchema, version: z.number().int().nonnegative(),
  sourceBalanceId: z.string().uuid(), sourceBalanceVersion: z.number().int(), sourceWarehouseCode: z.string(), sourceWarehouseName: z.string(),
  sourceLocationCode: z.string(), sourceLocationName: z.string(), targetLocationId: z.string().uuid(), targetLocationCode: z.string(),
  targetLocationName: z.string(), targetBalanceId: z.string().uuid().nullable(), targetBalanceVersion: z.number().int().nullable(),
  materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), lotNumber: z.string(), unit: z.string(),
  qualityStatus: z.literal("AVAILABLE"), quantity: z.number().positive(), sourceOutMovementId: z.string().uuid().nullable(),
  sourceOutMovementNumber: z.string().nullable(), targetInMovementId: z.string().uuid().nullable(), targetInMovementNumber: z.string().nullable(),
  reverseOutMovementId: z.string().uuid().nullable(), reverseOutMovementNumber: z.string().nullable(), reverseInMovementId: z.string().uuid().nullable(),
  reverseInMovementNumber: z.string().nullable(), createdByUsername: z.string(), createdAt: z.string(), completedByUsername: z.string().nullable(),
  completedAt: z.string().nullable(), cancelledByUsername: z.string().nullable(), cancelledAt: z.string().nullable(), cancellationReason: z.string().nullable(),
  reversedByUsername: z.string().nullable(), reversedAt: z.string().nullable(), reversalReason: z.string().nullable(), createRequestId: z.string(),
});

export const putawayReferenceDataSchema = z.object({
  sourceBalances: z.array(z.object({ id: z.string().uuid(), version: z.number().int().nonnegative(), warehouseId: z.string().uuid(),
    warehouseCode: z.string(), warehouseName: z.string(), locationId: z.string().uuid(), locationCode: z.string(), locationName: z.string(),
    materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), lotNumber: z.string(), unit: z.string(),
    availableQuantity: z.number().nonnegative(), reservedOpenQuantity: z.number().nonnegative() })),
  targetLocations: z.array(z.object({ id: z.string().uuid(), warehouseId: z.string().uuid(), warehouseCode: z.string(),
    code: z.string(), name: z.string(), scanCode: z.string() })),
});

export const putawayPageSchema = z.object({ items: z.array(putawayTaskSchema), totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(), size: z.number().int().positive(), totalPages: z.number().int().nonnegative() });

export type PutawayTask = z.infer<typeof putawayTaskSchema>;
export type PutawayReferenceData = z.infer<typeof putawayReferenceDataSchema>;
export type PutawayStatus = z.infer<typeof putawayStatusSchema>;

