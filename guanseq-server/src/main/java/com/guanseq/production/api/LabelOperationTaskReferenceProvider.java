package com.guanseq.production.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabelOperationTaskReferenceProvider {

	Optional<OperationTaskLabelReference> findLabelTask(UUID tenantOrganizationId, UUID taskId);

	List<OperationTaskLabelReference> listLabelTasks(UUID tenantOrganizationId, int limit);

	record OperationTaskLabelReference(UUID id, long version, String taskNumber, String operationName,
			String orderNumber, String workCenterCode, String status) { }
}

