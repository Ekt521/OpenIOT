package com.openiot.platform.mqtt;

import java.util.Map;

/**
 * 设备上报的遥测/状态载荷。
 * {"type":"telemetry","deviceCode":"OI-240018","ts":"2026-10-16T08:00:00Z","state":{"power":true}}
 */
public record TelemetryPayload(String type, String deviceCode, String ts, Map<String, Object> state) {
}
