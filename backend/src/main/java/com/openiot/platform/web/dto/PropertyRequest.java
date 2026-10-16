package com.openiot.platform.web.dto;

import java.util.List;

public record PropertyRequest(
    String id,
    String name,
    String key,
    String type,
    String unit,
    Double min,
    Double max,
    List<String> options) {
}
