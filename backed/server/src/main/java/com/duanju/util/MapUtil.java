package com.duanju.util;

import org.springframework.cglib.beans.BeanMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MapUtil {
    private MapUtil() {
    }

    /**
     * 将 Java Bean 转换为 snake_case 键的 Map，兼容旧代码中 Map&lt;String,Object&gt; 的用法。
     */
    public static Map<String, Object> beanToMap(Object bean) {
        if (bean == null) {
            return null;
        }
        BeanMap beanMap = BeanMap.create(bean);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object key : beanMap.keySet()) {
            String strKey = String.valueOf(key);
            Object value = beanMap.get(key);
            if (value != null) {
                result.put(camelToSnake(strKey), value);
            }
        }
        return result;
    }

    /**
     * 批量将 Bean 列表转换为 Map 列表。
     */
    public static <T> List<Map<String, Object>> beansToMaps(List<T> beans) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (beans == null) {
            return result;
        }
        for (T bean : beans) {
            result.add(beanToMap(bean));
        }
        return result;
    }

    public static String str(Map<String, Object> map, String key) {
        Object value = value(map, key);
        return value == null ? null : String.valueOf(value);
    }

    public static Long lng(Map<String, Object> map, String key) {
        Object value = value(map, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    public static Integer integer(Map<String, Object> map, String key) {
        Object value = value(map, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> castMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    public static boolean bool(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = value(map, key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object value(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        String camel = snakeToCamel(key);
        if (map.containsKey(camel)) {
            return map.get(camel);
        }
        String snake = camelToSnake(key);
        if (map.containsKey(snake)) {
            return map.get(snake);
        }
        return null;
    }

    private static String snakeToCamel(String key) {
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (char ch : key.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else if (upper) {
                builder.append(Character.toUpperCase(ch));
                upper = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String camelToSnake(String key) {
        StringBuilder builder = new StringBuilder();
        for (char ch : key.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
