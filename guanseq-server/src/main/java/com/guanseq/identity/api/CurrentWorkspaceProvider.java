package com.guanseq.identity.api;

public interface CurrentWorkspaceProvider {

	CurrentWorkspaceAccess resolve(String username);
}
