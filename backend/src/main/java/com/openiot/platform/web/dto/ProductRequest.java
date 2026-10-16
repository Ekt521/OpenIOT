package com.openiot.platform.web.dto;

import java.util.List;

public record ProductRequest(
    String name,
    String category,
    String description,
    String status,
    List<PropertyRequest> properties) {
}
