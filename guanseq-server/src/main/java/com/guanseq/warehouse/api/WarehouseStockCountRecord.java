package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record WarehouseStockCountRecord(UUID id,String countNumber,String status,long version,UUID balanceId,long currentBalanceVersion,
        String warehouseCode,String warehouseName,String locationCode,String locationName,String materialCode,String materialName,
        String materialSpecification,String lotNumber,String unit,String qualityStatus,BigDecimal bookOnHand,BigDecimal bookAllocated,
        BigDecimal bookFrozen,BigDecimal countedQuantity,BigDecimal differenceQuantity,long snapshotBalanceVersion,
        UUID adjustmentMovementId,String adjustmentMovementNumber,String adjustmentMovementType,UUID reverseMovementId,
        String reverseMovementNumber,String reverseMovementType,String countNote,String approvalComment,String createdByUsername,
        Instant createdAt,String countedByUsername,Instant countedAt,String approvedByUsername,Instant approvedAt,
        String cancelledByUsername,Instant cancelledAt,String cancellationReason,String reversedByUsername,Instant reversedAt,
        String reversalReason,String createRequestId) {
    public record CreateRequest(@NotNull UUID balanceId,@Min(0) long expectedBalanceVersion) { }
    public record RecordCountRequest(@Min(0) long expectedVersion,@Min(0) long expectedBalanceVersion,
            @NotNull @PositiveOrZero BigDecimal countedQuantity,@NotNull @Size(min=4,max=300) String note) { }
    public record ApproveRequest(@Min(0) long expectedVersion,@Min(0) long expectedBalanceVersion,
            @NotNull @Size(min=4,max=300) String comment) { }
    public record CancelRequest(@Min(0) long expectedVersion,@NotNull @Size(min=4,max=300) String reason) { }
    public record ReverseRequest(@Min(0) long expectedVersion,@Min(0) long expectedBalanceVersion,
            @NotNull @Size(min=4,max=300) String reason) { }
}
