package com.guanseq.procurement.api;

import java.util.List;
import java.util.UUID;

public record PurchaseOrderReferenceData(List<SupplierOption> suppliers, List<MaterialOption> materials) {

	public record SupplierOption(UUID id, String code, String name, String contactName, String contactPhone) { }

	public record MaterialOption(UUID id, String code, String name, String specification, String baseUnit) { }
}
