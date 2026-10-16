package com.openiot.platform.web.dto;

import java.util.Map;

public record CommandRequest(Map<String, Object> target) {
}
