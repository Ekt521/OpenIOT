package com.openiot.platform.web.dto;

public record MqttSettingsRequest(String host, Integer port, String clientId) {
}
