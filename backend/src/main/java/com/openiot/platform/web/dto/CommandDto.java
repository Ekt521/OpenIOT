package com.openiot.platform.web.dto;

import java.util.Map;

public record CommandDto(
    String id,
    String deviceId,
    String deviceName,
    String target,
    Map<String, Object> targetState,
    String createdAt,
    String status,
    String confirmation,
    String message) {
}
