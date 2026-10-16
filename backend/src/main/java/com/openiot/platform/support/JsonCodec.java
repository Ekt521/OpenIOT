package com.openiot.platform.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** 设备载荷与状态字段的 JSON 读写工具。 */
public final class JsonCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private JsonCodec() {
  }

  public static ObjectMapper mapper() {
    return MAPPER;
  }

  public static <T> T read(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (Exception ex) {
      throw new IllegalArgumentException("设备载荷解析失败：" + ex.getMessage(), ex);
    }
  }

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("序列化失败：" + ex.getMessage(), ex);
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return MAPPER.readValue(json, Map.class);
    } catch (Exception ex) {
      return new LinkedHashMap<>();
    }
  }
}
