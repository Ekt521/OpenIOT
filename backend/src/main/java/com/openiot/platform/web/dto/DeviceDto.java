package com.openiot.platform.web.dto;

import java.util.List;
import java.util.Map;

public record DeviceDto(
    String id,
    String productId,
    String name,
    String code,
    String location,
    String description,
    boolean enabled,
    boolean online,
    String lastSeen,
    String confirmation,
    String controlProfile,
    List<String> capabilities,
    Map<String, Object> state,
    Map<String, Object> targetState,
    List<Map<String, Object>> history,
    List<EventDto> events,
    String credential) {
}
