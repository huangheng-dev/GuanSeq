package com.guanseq.identity.api;

import java.util.UUID;

public interface LabelEmployeeReferenceProvider {

	EmployeeLabelReference resolveCurrentEmployee(CurrentWorkspaceAccess access);

	record EmployeeLabelReference(UUID id, long version, String username, String displayName) { }
}

