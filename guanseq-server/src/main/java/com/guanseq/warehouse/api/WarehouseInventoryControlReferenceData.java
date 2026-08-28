package com.guanseq.warehouse.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WarehouseInventoryControlReferenceData(List<Balance> balances,List<TargetLocation> targetLocations) {
    public record Balance(UUID id,long version,UUID warehouseId,String warehouseCode,String warehouseName,UUID locationId,
            String locationCode,String locationName,String locationType,String materialCode,String materialName,String materialSpecification,
            String lotNumber,String unit,String qualityStatus,BigDecimal onHandQuantity,BigDecimal allocatedQuantity,
            BigDecimal frozenQuantity,BigDecimal availableQuantity,BigDecimal reservedTransferQuantity,boolean activeCount) { }
    public record TargetLocation(UUID id,UUID warehouseId,String warehouseCode,String code,String name,String scanCode) { }
}
