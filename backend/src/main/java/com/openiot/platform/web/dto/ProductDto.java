package com.openiot.platform.web.dto;

import java.util.List;

public record ProductDto(
    String id,
    String name,
    String category,
    String description,
    String status,
    String protocol,
    String createdAt,
    List<PropertyDto> properties) {
}
