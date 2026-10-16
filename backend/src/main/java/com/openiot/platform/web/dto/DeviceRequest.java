package com.openiot.platform.web.dto;

public record DeviceRequest(
    String name,
    String productId,
    String code,
    String location,
    String description) {
}
