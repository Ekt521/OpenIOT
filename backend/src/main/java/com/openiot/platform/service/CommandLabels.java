package com.openiot.platform.service;

import com.openiot.platform.support.JsonCodec;
import java.util.Map;

/** 把结构化的目标状态转成界面上可读的命令文案。 */
public final class CommandLabels {

  private CommandLabels() {
  }

  public static String labelFor(Map<String, Object> target) {
    if (target == null || target.isEmpty()) {
      return "未指定目标";
    }
    Object power = target.get("power");
    if (power instanceof Boolean powerOn && target.size() == 1) {
      return powerOn ? "开启" : "关闭";
    }
    Object temperature = target.get("temperature");
    if (temperature != null) {
      StringBuilder builder = new StringBuilder("设置 ").append(trimNumber(temperature)).append("°C");
      Object mode = target.get("mode");
      if (mode instanceof String modeText && !modeText.isBlank()) {
        builder.append(' ').append(modeText);
      }
      return builder.toString();
    }
    if (power instanceof Boolean powerOn) {
      return powerOn ? "开启" : "关闭";
    }
    Object key = target.get("key");
    if (key instanceof String keyCode) {
      return switch (keyCode) {
        case "power" -> "电源（切换）";
        case "volumeup" -> "音量 +";
        case "volumedown" -> "音量 -";
        case "mute" -> "静音";
        case "home" -> "主页";
        case "back" -> "返回";
        case "up" -> "上";
        case "down" -> "下";
        case "left" -> "左";
        case "right" -> "右";
        case "enter" -> "确定";
        case "menu" -> "菜单";
        default -> "按键 " + keyCode;
      };
    }
    return JsonCodec.write(target);
  }

  private static String trimNumber(Object value) {
    if (value instanceof Number number) {
      double asDouble = number.doubleValue();
      if (asDouble == Math.floor(asDouble) && !Double.isInfinite(asDouble)) {
        return String.valueOf((long) asDouble);
      }
      return String.valueOf(asDouble);
    }
    return String.valueOf(value);
  }
}
