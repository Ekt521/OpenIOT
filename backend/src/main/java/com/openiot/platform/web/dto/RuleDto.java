package com.openiot.platform.web.dto;

public record RuleDto(
    String id,
    String name,
    String deviceId,
    String property,
    String operator,
    Double threshold,
    String level,
    boolean enabled) {
}
