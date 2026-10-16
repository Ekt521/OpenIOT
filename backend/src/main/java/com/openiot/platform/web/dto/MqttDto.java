package com.openiot.platform.web.dto;

public record MqttDto(
    String host,
    int port,
    String clientId,
    boolean connected,
    String lastCheck,
    String lastError) {
}
