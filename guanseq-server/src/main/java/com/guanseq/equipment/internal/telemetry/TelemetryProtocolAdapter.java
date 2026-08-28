package com.guanseq.equipment.internal.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Production protocol seam. Protocol adapters translate their native source
 * addresses and connection parameters into the shared telemetry value model.
 */
public interface TelemetryProtocolAdapter {

	String protocol();

	Map<UUID, RawValue> readAll(ConnectionSpec connection, List<PointSpec> points);

	default void close(ConnectionSpec connection) { }

	record ConnectionSpec(String host, int port, int connectTimeoutMs, int readTimeoutMs,
			Map<String, String> parameters) {
		public String requiredParameter(String name) {
			String value = parameters.get(name);
			if (value == null || value.isBlank()) {
				throw new TelemetryProtocolException("INVALID_CONFIGURATION", "采集连接缺少协议参数 " + name);
			}
			return value;
		}
	}

	record PointSpec(UUID id, String sourceType, String sourceAddress, String valuePath, String valueType) {
		public PointSpec(UUID id, String sourceType, String sourceAddress, String valueType) {
			this(id, sourceType, sourceAddress, null, valueType);
		}
	}

	record RawValue(String rawValue, BigDecimal numericValue, Boolean booleanValue,
			String sourceMessageId, Instant deviceTime) {
		public RawValue(String rawValue, BigDecimal numericValue, Boolean booleanValue) {
			this(rawValue, numericValue, booleanValue, null, null);
		}
	}

	class TelemetryProtocolException extends RuntimeException {
		private final String code;

		public TelemetryProtocolException(String code, String message) {
			super(message);
			this.code = code;
		}

		public TelemetryProtocolException(String code, String message, Throwable cause) {
			super(message, cause);
			this.code = code;
		}

		public String code() { return code; }
	}
}
