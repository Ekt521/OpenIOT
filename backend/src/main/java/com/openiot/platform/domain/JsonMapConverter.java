package com.openiot.platform.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把设备状态这类键值对存成 JSON 文本列。
 * 状态结构随产品属性变化，不适合拆成固定列。
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(Map<String, Object> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return "{}";
    }
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (Exception ex) {
      throw new IllegalStateException("设备状态序列化失败", ex);
    }
  }

  @Override
  public Map<String, Object> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return MAPPER.readValue(dbData, TYPE);
    } catch (Exception ex) {
      throw new IllegalStateException("设备状态反序列化失败", ex);
    }
  }
}
