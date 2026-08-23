package com.guanseq.finance.internal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 会计期间已关账异常。409 响应，附带 X-Period-Label 和 X-Error-Code 响应头。
 */
class PeriodClosedException extends ResponseStatusException {

	private final transient HttpHeaders customHeaders = new HttpHeaders();

	PeriodClosedException(String periodLabel) {
		super(HttpStatus.CONFLICT, "会计期间 " + periodLabel + " 已关账，不能记入该月业务");
		customHeaders.add("X-Period-Label", periodLabel);
		customHeaders.add("X-Error-Code", "PERIOD_CLOSED");
	}

	@Override
	public HttpHeaders getHeaders() {
		return customHeaders;
	}
}
