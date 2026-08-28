package com.guanseq.equipment.internal.telemetry;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
final class MqttCredentialResolver {

	Credentials resolve(String reference) {
		if (reference == null || reference.isBlank()) return null;
		String normalized = reference.trim().toUpperCase(Locale.ROOT).replace('-', '_');
		String environmentPrefix = "GUANSEQ_MQTT_CREDENTIAL_" + normalized;
		String propertyPrefix = "guanseq.mqtt.credential." + normalized.toLowerCase(Locale.ROOT);
		String username = first(System.getenv(environmentPrefix + "_USERNAME"),
				System.getProperty(propertyPrefix + ".username"));
		String password = first(System.getenv(environmentPrefix + "_PASSWORD"),
				System.getProperty(propertyPrefix + ".password"));
		if (username == null || password == null) {
			throw new TelemetryProtocolAdapter.TelemetryProtocolException("MQTT_CREDENTIAL_UNAVAILABLE",
					"MQTT 凭据引用未在服务端完整配置：" + reference.trim());
		}
		return new Credentials(username, password);
	}

	private static String first(String primary, String fallback) {
		if (primary != null && !primary.isBlank()) return primary;
		return fallback == null || fallback.isBlank() ? null : fallback;
	}

	record Credentials(String username, String password) { }
}
