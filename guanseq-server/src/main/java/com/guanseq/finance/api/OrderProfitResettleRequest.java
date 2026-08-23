package com.guanseq.finance.api;

import java.time.LocalDate;

public record OrderProfitResettleRequest(String reason, LocalDate settlementDate, Long expectedVersion) { }
