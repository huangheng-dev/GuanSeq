package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WarehouseTransferRecord(UUID id,String taskNumber,String status,long version,UUID sourceBalanceId,long sourceBalanceVersion,
        String sourceWarehouseCode,String sourceWarehouseName,String sourceLocationCode,String sourceLocationName,UUID targetLocationId,
        String targetLocationCode,String targetLocationName,UUID targetBalanceId,Long targetBalanceVersion,String materialCode,String materialName,
        String materialSpecification,String lotNumber,String unit,String qualityStatus,BigDecimal quantity,String transferReason,
        UUID sourceOutMovementId,String sourceOutMovementNumber,UUID targetInMovementId,String targetInMovementNumber,
        UUID reverseOutMovementId,String reverseOutMovementNumber,UUID reverseInMovementId,String reverseInMovementNumber,
        String createdByUsername,Instant createdAt,String completedByUsername,Instant completedAt,String cancelledByUsername,
        Instant cancelledAt,String cancellationReason,String reversedByUsername,Instant reversedAt,String reversalReason,String createRequestId) {
    public record CreateRequest(@NotNull UUID sourceBalanceId,@NotNull UUID targetLocationId,@NotNull @Positive BigDecimal quantity,
            @Min(0) long expectedSourceBalanceVersion,@NotNull @Size(min=4,max=300) String reason) { }
    public record CompleteRequest(@Min(0) long expectedVersion,@Min(0) long expectedSourceBalanceVersion) { }
    public record CancelRequest(@Min(0) long expectedVersion,@NotNull @Size(min=4,max=300) String reason) { }
    public record ReverseRequest(@Min(0) long expectedVersion,@Min(0) long expectedTargetBalanceVersion,
            @NotNull @Size(min=4,max=300) String reason) { }
}
