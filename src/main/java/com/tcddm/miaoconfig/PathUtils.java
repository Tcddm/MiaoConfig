package com.tcddm.miaoconfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PathUtils {
    private static final String PATH_SEPARATOR = "\\.";

    /**
     * 从嵌套Map中根据路径获取值
     * @param map 嵌套配置Map
     * @param path 路径（如"a.b.c"）
     * @return 对应路径的值，不存在则返回null
     */
    public static Object getValue(Map<String, Object> map, String path) {
        if (map == null || path == null || path.isEmpty()) {
            return null;
        }

        String[] keys = path.split(PATH_SEPARATOR);
        Map<String, Object> current = map;

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            Object value = current.get(key);
            if (value == null) {
                return null;
            }
            // 如果是最后一个key，直接返回值
            if (i == keys.length - 1) {
                return value;
            }
            // 否则继续遍历嵌套Map
            if (value instanceof Map) {
                current = (Map<String, Object>) value;
            } else {
                return null; // 中间节点不是Map，路径无效
            }
        }
        return null;
    }

    /**
     * 向嵌套Map中根据路径设置值
     * @param map 嵌套配置Map
     * @param path 路径（如"a.b.c"）
     * @param value 要设置的值
     */
    public static boolean setValue(Map<String, Object> map, String path, Object value) {
        if (map == null || path == null || path.isEmpty()) {
            return false;
        }

        String[] keys = path.split(PATH_SEPARATOR);
        Map<String, Object> current = map;

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            // 最后一个key直接设值
            if (i == keys.length - 1) {
                Object existingValue = current.get(key);
                // 1. 先判断是否为同一对象（快速短路）
                if (existingValue == value) {
                    return false;
                }
                // 2. 处理null情况
                if (existingValue == null && value == null) {
                    return false;
                }
                if (existingValue == null || value == null) {
                    // 一方为null，另一方非null：更新并返回true
                    current.put(key, value);
                    return true;
                }
                // 3. 类型转换（确保对比公平）
                Object convertedExisting = TypeConverter.convertValue(existingValue, value.getClass());
                // 处理转换失败的null情况
                if (convertedExisting == null) {
                    // 转换失败时，默认值不相等
                    current.put(key, value);
                    return true;
                }
                // 4. 分类型对比
                boolean valuesEqual;
                if (value.getClass().isArray()) {
                    valuesEqual = Objects.deepEquals(convertedExisting, value);
                } else if (value instanceof Enum || existingValue instanceof Enum) {
                    // 枚举对比（已通过TypeConverter转换类型）
                    valuesEqual = value.equals(convertedExisting);
                } else if (value instanceof Number && existingValue instanceof Number) {
                    // 数字类型兼容对比（如float和double）
                    valuesEqual = compareNumbers((Number) value, (Number) convertedExisting);
                } else {
                    // 其他类型直接用equals
                    valuesEqual = value.equals(convertedExisting);
                }
                // 5. 判断是否更新
                if (valuesEqual) {
                    return false; // 未更新
                } else {
                    current.put(key, value);
                    return true; // 已更新
                }
            }
            // 中间节点不存在则创建新Map
            Object next = current.get(key);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                Map<String, Object> newMap = new HashMap<>();
                current.put(key, newMap);
                current = newMap;
            }
        }
        return false;
    }
    private static boolean compareNumbers(Number a, Number b) {
        if (a instanceof Double && b instanceof Float) {
            return a.doubleValue() == b.doubleValue();
        }
        if (a instanceof Float && b instanceof Double) {
            return a.doubleValue() == b.doubleValue();
        }
        return a.equals(b);
    }
}