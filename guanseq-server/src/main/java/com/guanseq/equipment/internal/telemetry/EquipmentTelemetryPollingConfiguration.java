package com.guanseq.equipment.internal.telemetry;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class EquipmentTelemetryPollingConfiguration { }
