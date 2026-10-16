package com.openiot.platform.web.dto;

import java.util.List;

public record PropertyDto(
    String id,
    String name,
    String key,
    String type,
    String unit,
    Double min,
    Double max,
    List<String> options) {
}
