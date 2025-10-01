package com.tcddm.miaoconfig;

import java.util.HashMap;
import java.util.Map;

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
    public static void setValue(Map<String, Object> map, String path, Object value) {
        if (map == null || path == null || path.isEmpty()) {
            return;
        }

        String[] keys = path.split(PATH_SEPARATOR);
        Map<String, Object> current = map;

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            // 最后一个key直接设值
            if (i == keys.length - 1) {
                current.put(key, value);
                return;
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
    }
}