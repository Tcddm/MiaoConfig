package com.tcddm.miaoconfig;

import com.tcddm.miaoconfig.egg.MiaoLogger;

import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 类型转换工具类，支持如下：
 基本数据类型（int/boolean/long等）及其包装类（Integer/Boolean等）
 字符串（String）与各类数值、布尔值、字符的互转
 枚举类型（Enum）：支持名称（含大小写兼容）、序号等多种匹配方式
 集合类型（List）：支持字符串（逗号分隔）、数组、Iterable等转换为List
 数组类型：支持从List、字符串、其他数组转换为指定类型数组
 日期时间类型（LocalDate、LocalDateTime）：支持标准格式字符串转换
 Optional类型：自动包装转换结果为Optional对象
 目前该类还不够完善，可能有意想不到的问题（特别是null的处理），实在是没办法了，祈求不要出问题qwq
 **/
public class TypeConverter {
    private static final MiaoLogger logger = MiaoLogger.getLogger(TypeConverter.class);

    // 使用ConcurrentHashMap提高线程安全性
    private static final Map<Class<?>, Function<ConvertContext, Object>> CONVERTERS = new HashMap<>();

    // 转换上下文，携带必要的转换信息
    private static class ConvertContext {
        final Object value;
        final boolean throwOnFailure;

        ConvertContext(Object value, boolean throwOnFailure) {
            this.value = value;
            this.throwOnFailure = throwOnFailure;
        }
    }

    static {
        // 字符串转换
        CONVERTERS.put(String.class, ctx -> ctx.value != null ? ctx.value.toString() : null);

        // 整数类型转换
        CONVERTERS.put(int.class, ctx -> parseNumber(ctx, Integer::parseInt, 0));
        CONVERTERS.put(Integer.class, ctx -> parseNumber(ctx, Integer::parseInt, null));

        // 布尔类型转换
        CONVERTERS.put(boolean.class, ctx -> parseBoolean(ctx, false));
        CONVERTERS.put(Boolean.class, ctx -> parseBoolean(ctx, null));

        // 长整数类型转换
        CONVERTERS.put(long.class, ctx -> parseNumber(ctx, Long::parseLong, 0L));
        CONVERTERS.put(Long.class, ctx -> parseNumber(ctx, Long::parseLong, null));

        // 双精度类型转换
        CONVERTERS.put(double.class, ctx -> parseNumber(ctx, Double::parseDouble, 0.0));
        CONVERTERS.put(Double.class, ctx -> parseNumber(ctx, Double::parseDouble, null));

        // 单精度类型转换
        CONVERTERS.put(float.class, ctx -> parseNumber(ctx, Float::parseFloat, 0.0f));
        CONVERTERS.put(Float.class, ctx -> parseNumber(ctx, Float::parseFloat, null));

        // 短整数类型转换
        CONVERTERS.put(short.class, ctx -> parseNumber(ctx, Short::parseShort, (short) 0));
        CONVERTERS.put(Short.class, ctx -> parseNumber(ctx, Short::parseShort, null));

        // 字节类型转换
        CONVERTERS.put(byte.class, ctx -> parseNumber(ctx, Byte::parseByte, (byte) 0));
        CONVERTERS.put(Byte.class, ctx -> parseNumber(ctx, Byte::parseByte, null));

        // 字符类型转换
        CONVERTERS.put(char.class, ctx -> parseChar(ctx, '\0'));
        CONVERTERS.put(Character.class, ctx -> parseChar(ctx, null));

        // 日期类型转换
        CONVERTERS.put(LocalDate.class, ctx -> parseLocalDate(ctx, null));
        CONVERTERS.put(LocalDateTime.class, ctx -> parseLocalDateTime(ctx, null));

        // Optional类型转换
        CONVERTERS.put(Optional.class, ctx -> Optional.ofNullable(convertValue(ctx.value, Object.class, ctx.throwOnFailure)));
    }

    /**
     * 类型转换方法，通过参数控制转换失败时的行为
     * @param value 要转换的值
     * @param targetType 目标类型
     * @param throwOnFailure 转换失败时是否抛出异常
     * @return 转换后的值或原始值
     * @throws IllegalArgumentException 当转换失败且throwOnFailure为true时抛出
     */
    public static Object convertValue(Object value, Class<?> targetType, boolean throwOnFailure) {

        // 如果类型已匹配，直接返回
        if (targetType.isInstance(value)) {
            return value;
        }

        // 处理基本类型的包装类转换
        Class<?> wrapperType = getWrapperType(targetType);
        if (wrapperType != null && wrapperType.isInstance(value)) {
            return value;
        }

        // 尝试使用预定义的转换策略
        Function<ConvertContext, Object> converter = CONVERTERS.get(targetType);
        if (converter != null) {
            try {
                Object result = converter.apply(new ConvertContext(value, throwOnFailure));
                // 检查转换结果是否有效
                if (result != null || !targetType.isPrimitive()) {
                    return result;
                }
            } catch (IllegalArgumentException e) {
                return handleConversionError(value, targetType, throwOnFailure, e);
            }
        }

        // 处理枚举类型
        if (targetType.isEnum()) {
            Object enumResult = convertToEnum(value, targetType, throwOnFailure);
            if (enumResult != null) {
                return enumResult;
            } else if (throwOnFailure) {
                throw new IllegalArgumentException(
                        buildErrorMessage(value, targetType, "枚举转换失败")
                );
            }
        }

        // 处理列表类型
        if (List.class.isAssignableFrom(targetType)) {
            try {
                return convertToList(value, throwOnFailure);
            } catch (Exception e) {
                return handleConversionError(value, targetType, throwOnFailure, e);
            }
        }

        // 处理数组类型
        if (targetType.isArray()) {
            Object arrayResult = convertToArray(value, targetType, throwOnFailure);
            if (arrayResult != null) {
                return arrayResult;
            } else if (throwOnFailure) {
                throw new IllegalArgumentException(
                        buildErrorMessage(value, targetType, "数组转换失败")
                );
            }
        }

        // 无法转换时根据参数决定行为
        return handleUnsupportedConversion(value, targetType, throwOnFailure);
    }

    /**
     * 转换失败时返回原始值的便捷方法
     */
    public static Object convertValue(Object value, Class<?> targetType) {
        return convertValue(value, targetType, false);
    }



    /**
     * 转换为枚举类型
     */
    private static Object convertToEnum(Object value, Class<?> targetType, boolean throwOnFailure) {
        try {
            String enumName = value.toString().trim();
            // 先尝试精确匹配
            return Enum.valueOf((Class<Enum>) targetType, enumName);
        } catch (IllegalArgumentException e) {
            // 尝试忽略大小写匹配
            String enumName = value.toString().trim().toUpperCase();
            for (Enum<?> enumConstant : (Enum<?>[]) targetType.getEnumConstants()) {
                if (enumConstant.name().equals(enumName) ||
                        enumConstant.name().equalsIgnoreCase(enumName)) {
                    return enumConstant;
                }
            }

            // 尝试通过序号匹配
            try {
                int ordinal = Integer.parseInt(value.toString().trim());
                Enum<?>[] constants = (Enum<?>[]) targetType.getEnumConstants();
                if (ordinal >= 0 && ordinal < constants.length) {
                    return constants[ordinal];
                }
            } catch (NumberFormatException ignored) {
                // 不是数字格式，忽略
            }

            String errorMsg = buildErrorMessage(value, targetType, "枚举转换失败");
            if (throwOnFailure) {
                throw new IllegalArgumentException(errorMsg, e);
            } else {
                logger.warn(errorMsg, e);
                return null;
            }
        }
    }

    /**
     * 转换为列表
     */
    private static List<?> convertToList(Object value, boolean throwOnFailure) {
        if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.isEmpty()) {
                return Collections.emptyList();
            }
            // 支持逗号分隔的字符串转换为列表，并自动转换元素类型
            return Arrays.stream(strValue.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        } else if (value.getClass().isArray()) {
            return Arrays.stream((Object[]) value).collect(Collectors.toList());
        } else if (value instanceof Iterable) {
            List<Object> list = new ArrayList<>();
            ((Iterable<?>) value).forEach(item -> list.add(convertValue(item, Object.class, throwOnFailure)));
            return list;
        }
        return Collections.singletonList(value);
    }

    /**
     * 转换为数组
     */
    private static Object convertToArray(Object value, Class<?> targetType, boolean throwOnFailure) {
        try {
            Class<?> componentType = targetType.getComponentType();
            List<?> list = convertToList(value, throwOnFailure);

            Object array = Array.newInstance(componentType, list.size());
            for (int i = 0; i < list.size(); i++) {
                Object element = convertValue(list.get(i), componentType, throwOnFailure);
                Array.set(array, i, element);
            }
            return array;
        } catch (Exception e) {
            String errorMsg = buildErrorMessage(value, targetType, "数组转换失败");
            if (throwOnFailure) {
                throw new IllegalArgumentException(errorMsg, e);
            } else {
                logger.warn(errorMsg, e);
                return null;
            }
        }
    }

    /**
     * 解析数字
     */
    private static <T> T parseNumber(ConvertContext ctx, Function<String, T> parser, T defaultValue) {
        try {
            String strValue = ctx.value.toString().trim();
            return parser.apply(strValue);
        } catch (NumberFormatException e) {
            String errorMsg = buildErrorMessage(ctx.value, parser.getClass(), "数字转换失败");
            if (ctx.throwOnFailure) {
                throw new IllegalArgumentException(errorMsg, e);
            }
            logger.warn("{}，使用默认值 {}", errorMsg, defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * 解析布尔值
     */
    private static Boolean parseBoolean(ConvertContext ctx, Boolean defaultValue) {
        if (ctx.value instanceof Number) {
            // 数字0为false，其他为true
            return ((Number) ctx.value).doubleValue() != 0;
        }

        String strValue = ctx.value.toString().trim().toLowerCase();
        if ("true".equals(strValue) || "1".equals(strValue) || "yes".equals(strValue) || "y".equals(strValue)) {
            return true;
        }
        if ("false".equals(strValue) || "0".equals(strValue) || "no".equals(strValue) || "n".equals(strValue)) {
            return false;
        }

        String errorMsg = buildErrorMessage(ctx.value, Boolean.class, "布尔值转换失败");
        if (ctx.throwOnFailure) {
            throw new IllegalArgumentException(errorMsg);
        }

        logger.warn("{}，使用默认值 {}", errorMsg, defaultValue);
        return defaultValue;
    }

    /**
     * 解析字符
     */
    private static Character parseChar(ConvertContext ctx, Character defaultValue) {
        try {
            String strValue = ctx.value.toString().trim();
            if (strValue.length() == 1) {
                return strValue.charAt(0);
            }
            // 尝试解析为Unicode字符
            if (strValue.startsWith("\\u")) {
                return (char) Integer.parseInt(strValue.substring(2), 16);
            }
            // 尝试解析为ASCII码
            int code = Integer.parseInt(strValue);
            return (char) code;
        } catch (Exception e) {
            String errorMsg = buildErrorMessage(ctx.value, Character.class, "字符转换失败");
            if (ctx.throwOnFailure) {
                throw new IllegalArgumentException(errorMsg, e);
            }
            logger.warn("{}，使用默认值 {}", errorMsg, defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * 解析LocalDate
     */
    private static LocalDate parseLocalDate(ConvertContext ctx, LocalDate defaultValue) {
        try {
            return LocalDate.parse(ctx.value.toString().trim());
        } catch (DateTimeParseException e) {
            String errorMsg = buildErrorMessage(ctx.value, LocalDate.class, "日期转换失败");
            if (ctx.throwOnFailure) {
                throw new IllegalArgumentException(errorMsg, e);
            }
            logger.warn("{}，使用默认值 {}", errorMsg, defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * 解析LocalDateTime
     */
    private static LocalDateTime parseLocalDateTime(ConvertContext ctx, LocalDateTime defaultValue) {
        try {
            return LocalDateTime.parse(ctx.value.toString().trim());
        } catch (DateTimeParseException e) {
            String errorMsg = buildErrorMessage(ctx.value, LocalDateTime.class, "日期时间转换失败");
            if (ctx.throwOnFailure) {
                throw new IllegalArgumentException(errorMsg, e);
            }
            logger.warn("{}，使用默认值 {}", errorMsg, defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * 构建统一格式的错误消息
     */
    private static String buildErrorMessage(Object value, Class<?> targetType, String reason) {
        return String.format("%s: 从 %s(%s) 到 %s",
                reason,
                value.getClass().getSimpleName(),
                value,
                targetType.getSimpleName());
    }

    /**
     * 处理转换错误
     */
    private static Object handleConversionError(Object value, Class<?> targetType, boolean throwOnFailure, Exception e) {
        if (throwOnFailure) {
            throw new IllegalArgumentException(buildErrorMessage(value, targetType, "转换失败"), e);
        } else {
            logger.warn(buildErrorMessage(value, targetType, "转换失败"), e);
            return value;
        }
    }

    /**
     * 处理不支持的转换类型
     */
    private static Object handleUnsupportedConversion(Object value, Class<?> targetType, boolean throwOnFailure) {
        String errorMsg = buildErrorMessage(value, targetType, "不支持的类型转换");

        if (throwOnFailure) {
            throw new IllegalArgumentException(errorMsg);
        } else {
            logger.warn(errorMsg);
            return value;
        }
    }

    /**
     * 获取基本类型对应的包装类型
     */
    private static Class<?> getWrapperType(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }

        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;

        return null;
    }
}
