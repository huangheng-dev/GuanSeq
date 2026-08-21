package com.guanseq;

import org.springframework.boot.SpringApplication;

public class TestGuanSeqServerApplication {

	public static void main(String[] args) {
		SpringApplication.from(GuanSeqServerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
