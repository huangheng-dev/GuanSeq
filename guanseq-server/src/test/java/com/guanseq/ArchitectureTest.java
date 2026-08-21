package com.guanseq;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {

	@Test
	void verifiesModuleBoundaries() {
		ApplicationModules.of(GuanSeqServerApplication.class).verify();
	}
}
