package com.openiot.platform.web.dto;

public record RuleRequest(
    String id,
    String name,
    String deviceId,
    String property,
    String operator,
    Double threshold,
    String level,
    Boolean enabled) {
}
