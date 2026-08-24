package com.guanseq.identity.api;

import java.util.Optional;

/** Resolves an externally authenticated username to an active GuanSeq user fact. */
public interface ActiveIdentityProvider {

	Optional<String> findActiveUsername(String username);
}
