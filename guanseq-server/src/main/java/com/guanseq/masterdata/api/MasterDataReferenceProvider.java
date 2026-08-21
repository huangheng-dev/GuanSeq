package com.guanseq.masterdata.api;

import java.util.List;
import java.util.UUID;

public interface MasterDataReferenceProvider {

	CustomerReference requireActiveCustomer(UUID tenantOrganizationId, UUID customerId);

	MaterialReference requireActiveMaterial(UUID tenantOrganizationId, UUID materialId);

	List<CustomerReference> listActiveCustomers(UUID tenantOrganizationId);

	List<MaterialReference> listActiveMaterials(UUID tenantOrganizationId);

	record CustomerReference(UUID id, String code, String name, String creditLevel) {
	}

	record MaterialReference(UUID id, String code, String name, String specification, String baseUnit, String procurementType, boolean incomingInspectionRequired) {
	}
}

