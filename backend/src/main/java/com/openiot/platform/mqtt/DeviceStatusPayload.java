package com.openiot.platform.mqtt;

/**
 * 设备上线/离线通知，通常由 MQTT 遗嘱消息（LWT）发出并保留。
 * {"type":"status","deviceCode":"OI-240018","online":false}
 */
public record DeviceStatusPayload(String type, String deviceCode, Boolean online) {
}
