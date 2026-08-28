import { z } from "zod";

export const transferStatusSchema = z.enum(["OPEN", "COMPLETED", "CANCELLED", "REVERSED"]);
export const stockCountStatusSchema = z.enum(["OPEN", "COUNTED", "APPROVED", "CANCELLED", "REVERSED"]);

export const inventoryControlBalanceSchema = z.object({
  id: z.string().uuid(), version: z.number().int().nonnegative(), warehouseId: z.string().uuid(), warehouseCode: z.string(),
  warehouseName: z.string(), locationId: z.string().uuid(), locationCode: z.string(), locationName: z.string(), locationType: z.string(),
  materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), lotNumber: z.string(), unit: z.string(),
  qualityStatus: z.enum(["AVAILABLE", "INSPECTION", "BLOCKED"]), onHandQuantity: z.number().nonnegative(),
  allocatedQuantity: z.number().nonnegative(), frozenQuantity: z.number().nonnegative(), availableQuantity: z.number().nonnegative(),
  reservedTransferQuantity: z.number().nonnegative(), activeCount: z.boolean(),
});

export const inventoryControlReferenceDataSchema = z.object({
  balances: z.array(inventoryControlBalanceSchema),
  targetLocations: z.array(z.object({ id: z.string().uuid(), warehouseId: z.string().uuid(), warehouseCode: z.string(),
    code: z.string(), name: z.string(), scanCode: z.string() })),
});

export const transferTaskSchema = z.object({
  id: z.string().uuid(), taskNumber: z.string(), status: transferStatusSchema, version: z.number().int().nonnegative(),
  sourceBalanceId: z.string().uuid(), sourceBalanceVersion: z.number().int(), sourceWarehouseCode: z.string(), sourceWarehouseName: z.string(),
  sourceLocationCode: z.string(), sourceLocationName: z.string(), targetLocationId: z.string().uuid(), targetLocationCode: z.string(),
  targetLocationName: z.string(), targetBalanceId: z.string().uuid().nullable(), targetBalanceVersion: z.number().int().nullable(),
  materialCode: z.string(), materialName: z.string(), materialSpecification: z.string().nullable(), lotNumber: z.string(), unit: z.string(),
  qualityStatus: z.literal("AVAILABLE"), quantity: z.number().positive(), transferReason: z.string(),
  sourceOutMovementId: z.string().uuid().nullable(), sourceOutMovementNumber: z.string().nullable(),
  targetInMovementId: z.string().uuid().nullable(), targetInMovementNumber: z.string().nullable(),
  reverseOutMovementId: z.string().uuid().nullable(), reverseOutMovementNumber: z.string().nullable(),
  reverseInMovementId: z.string().uuid().nullable(), reverseInMovementNumber: z.string().nullable(),
  createdByUsername: z.string(), createdAt: z.string(), completedByUsername: z.string().nullable(), completedAt: z.string().nullable(),
  cancelledByUsername: z.string().nullable(), cancelledAt: z.string().nullable(), cancellationReason: z.string().nullable(),
  reversedByUsername: z.string().nullable(), reversedAt: z.string().nullable(), reversalReason: z.string().nullable(), createRequestId: z.string(),
});

export const stockCountTaskSchema = z.object({
  id: z.string().uuid(), countNumber: z.string(), status: stockCountStatusSchema, version: z.number().int().nonnegative(),
  balanceId: z.string().uuid(), currentBalanceVersion: z.number().int(), warehouseCode: z.string(), warehouseName: z.string(),
  locationCode: z.string(), locationName: z.string(), materialCode: z.string(), materialName: z.string(),
  materialSpecification: z.string().nullable(), lotNumber: z.string(), unit: z.string(), qualityStatus: z.enum(["AVAILABLE", "INSPECTION", "BLOCKED"]),
  bookOnHand: z.number().nonnegative(), bookAllocated: z.number().nonnegative(), bookFrozen: z.number().nonnegative(),
  countedQuantity: z.number().nonnegative().nullable(), differenceQuantity: z.number().nullable(), snapshotBalanceVersion: z.number().int().nonnegative(),
  adjustmentMovementId: z.string().uuid().nullable(), adjustmentMovementNumber: z.string().nullable(),
  adjustmentMovementType: z.enum(["RECEIPT", "ISSUE"]).nullable(), reverseMovementId: z.string().uuid().nullable(),
  reverseMovementNumber: z.string().nullable(), reverseMovementType: z.enum(["RECEIPT", "ISSUE"]).nullable(),
  countNote: z.string().nullable(), approvalComment: z.string().nullable(), createdByUsername: z.string(), createdAt: z.string(),
  countedByUsername: z.string().nullable(), countedAt: z.string().nullable(), approvedByUsername: z.string().nullable(), approvedAt: z.string().nullable(),
  cancelledByUsername: z.string().nullable(), cancelledAt: z.string().nullable(), cancellationReason: z.string().nullable(),
  reversedByUsername: z.string().nullable(), reversedAt: z.string().nullable(), reversalReason: z.string().nullable(), createRequestId: z.string(),
});

export const transferPageSchema = z.object({ items: z.array(transferTaskSchema), totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(), size: z.number().int().positive(), totalPages: z.number().int().nonnegative() });
export const stockCountPageSchema = z.object({ items: z.array(stockCountTaskSchema), totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(), size: z.number().int().positive(), totalPages: z.number().int().nonnegative() });

export type InventoryControlReferenceData = z.infer<typeof inventoryControlReferenceDataSchema>;
export type InventoryControlBalance = z.infer<typeof inventoryControlBalanceSchema>;
export type TransferTask = z.infer<typeof transferTaskSchema>;
export type StockCountTask = z.infer<typeof stockCountTaskSchema>;
