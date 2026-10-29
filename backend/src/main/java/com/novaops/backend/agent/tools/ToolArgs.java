package com.novaops.backend.agent.tools;

import java.util.Map;

/**
 * 工具入参读取的统一小工具：类型宽松（模型可能给出字符串数字）、
 * 必填缺失/数值越界返回 null 交由工具转成失败结果。
 */
public final class ToolArgs {

  private ToolArgs() {
  }

  public static String asString(Map<String, Object> args, String key) {
    Object value = args == null ? null : args.get(key);
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() || "null".equals(text) ? null : text;
  }

  public static String requireString(Map<String, Object> args, String key) {
    String value = asString(args, key);
    if (value == null) {
      throw new IllegalArgumentException("缺少必填参数：" + key);
    }
    return value;
  }

  public static Integer asInt(Map<String, Object> args, String key, Integer fallback, int min, int max) {
    Object value = args == null ? null : args.get(key);
    if (value == null || "".equals(value)) {
      return fallback;
    }
    int parsed;
    try {
      parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
    return Math.max(min, Math.min(max, parsed));
  }
}
