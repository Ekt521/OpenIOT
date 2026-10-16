package com.openiot.platform.web.dto;

public record AlarmDto(
    String id,
    String ruleId,
    String ruleName,
    String deviceId,
    String deviceName,
    String message,
    String level,
    String status,
    String createdAt,
    String acknowledgedAt,
    String closedAt,
    String note) {
}
