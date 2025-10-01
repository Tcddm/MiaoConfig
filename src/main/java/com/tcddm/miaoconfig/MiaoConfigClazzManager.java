package com.tcddm.miaoconfig;

import com.tcddm.miaoconfig.annotation.MiaoConfig;
import com.tcddm.miaoconfig.annotation.MiaoValue;
import com.tcddm.miaoconfig.egg.MiaoLogger;
import com.tcddm.miaoconfig.exception.MiaoConfigReadException;
import com.tcddm.miaoconfig.exception.MiaoConfigSetException;
import com.tcddm.miaoconfig.parser.MiaoConfigParser;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class MiaoConfigClazzManager<T> {
    private static final MiaoLogger logger=MiaoLogger.getLogger(MiaoConfigClazzManager.class);
    // 存储弱引用包装的实例（键：弱引用，值：实例本身，仅用于方便获取）
    private final CopyOnWriteArrayList<WeakReference<T>> container = new CopyOnWriteArrayList<>();
    // 引用队列：当弱引用关联的对象被回收时，弱引用会被加入此队列
    private final ReferenceQueue<T> referenceQueue = new ReferenceQueue<>();

    /**
     * 添加实例到容器
     */
    private void add(T instance) {
        // 清理已被回收的引用
        cleanUp();
        // 创建弱引用并关联引用队列，然后存入容器
        WeakReference<T> weakRef = new WeakReference<>(instance, referenceQueue);
        container.add(weakRef);
    }

    public MiaoConfigClazzManager<T> load(T instance) {
        if (!instance.getClass().isAnnotationPresent(com.tcddm.miaoconfig.annotation.MiaoConfig.class)) {
            logger.debug("实例缺少注解: {}", instance.getClass().getSimpleName());
            return this;
        }
        //加入维护表
        add(instance);

        MiaoConfig miaoConfigAnnotation = instance.getClass().getAnnotation(MiaoConfig.class);
        String configName = miaoConfigAnnotation.configName();
        try {

            // 注入配置值
            setFieldsFromMap(instance, MiaoConfigFileManager.getConfigData(configName),miaoConfigAnnotation.path());

            logger.info("配置注入完成: {}", instance.toString());
        } catch (IOException e) {
            handleConfigError(instance.toString(), "读取配置文件失败", configName, e);
        } catch (Exception e) {
            handleConfigError(instance.toString(), "解析配置文件失败", configName, e);
        }

        return this;
    }

    public void saveAllConfig() {

        for (T instance : getAliveInstances()) {
            saveConfig(instance);
        }
    }

    private Map<String, Object> saveEdit(T instance, MiaoConfigFileManager.MiaoConfigFile miaoConfigFile) {
        return miaoConfigFile.putConfigAndGet(getMapForClazz(instance, true));
    }

    public void saveConfig(T instance) {
        if (!instance.getClass().isAnnotationPresent(com.tcddm.miaoconfig.annotation.MiaoConfig.class)) {
            logger.debug("实例缺少注解: {}", instance.getClass().getSimpleName());
            return;
        }
        MiaoConfig miaoConfigAnnotation = instance.getClass().getAnnotation(MiaoConfig.class);
        String configName = miaoConfigAnnotation.configName();
        try {
            // 获取配置文件
            MiaoConfigFileManager.MiaoConfigFile miaoConfigFile = MiaoConfigFactory.getConfigFileManager().getForName(configName);
            Path configPath = miaoConfigFile.getFilePath(); // 替换File为Path
            // NIO方式检查文件是否存在
            if (configPath == null || !Files.exists(configPath) || !Files.isRegularFile(configPath)) {
                handleConfigError(instance.toString(), "配置文件不存在或不是常规文件", configName, null);
                return;
            }
            //反序列化
            MiaoConfigParser miaoConfigParser = MiaoConfigFactory.getParser(configPath.getFileName().toString());
            Map<String, Object> configMap = saveEdit(instance, miaoConfigFile);
            //判断配置是否相同
            if (configMap.hashCode() == miaoConfigFile.getConfigHash()) {
                logger.info("配置保存完成,但是由于没有更改并未写入文件: {}", instance.toString());
                return;
            }
            String temp = miaoConfigParser.serialize(configMap);
            //写入文件
            Files.write(
                    configPath,
                    temp.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE
            );
            //完成
            logger.info("配置保存完成: {}", instance.toString());

        } catch (IOException e) {
            handleConfigError(instance.toString(), "写入配置文件失败", configName, e);
        } catch (Exception e) {
            handleConfigError(instance.toString(), "保存配置文件失败", configName, e);
        }
    }
    // 构建完整路径：主节点路径 + 字段路径
    private static String buildFullPath(String mainPath, String fieldPath) {
        if (mainPath == null || mainPath.isEmpty()) {
            return fieldPath;
        }
        if (fieldPath == null || fieldPath.isEmpty()) {
            return mainPath;
        }
        return mainPath + "." + fieldPath;
    }
    private static Map<String, Field> getAllField(Class clazz) {
     /*   Map<String, Field> fieldMap = new HashMap<>();

        // 预先收集所有字段
        for (Field field : clazz.getDeclaredFields()) {
            if(!field.isAnnotationPresent(MiaoValue.class)){continue;}
            fieldMap.put(field.getName(), field);
        }
        return fieldMap;*/
        Map<String, Field> fieldMap = new HashMap<>();
        Class<?> currentClass = clazz;

        // 递归处理当前类和父类
        while (currentClass != null && !currentClass.equals(Object.class)) {
            for (Field field : currentClass.getDeclaredFields()) {
                // 只保留带注解的字段，子类覆盖父类
                if (field.isAnnotationPresent(MiaoValue.class) && !fieldMap.containsKey(field.getName())) {
                    fieldMap.put(field.getName(), field);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return fieldMap;
    }

    /*private static Object convertValue(Object value, Class<?> targetType) {
        try {
            if (targetType == String.class) {
                return value.toString();
            } else if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(value.toString());
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(value.toString());
            } else if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(value.toString());
            } else if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(value.toString());
            } else if (targetType == List.class && value instanceof String) {
                return Arrays.asList(((String) value).split(","));
            } else if (targetType.isEnum() && value instanceof String) {
                // 枚举类型支持
                return Enum.valueOf((Class<Enum>) targetType, value.toString().toUpperCase());
            }
            return value;
        } catch (Exception e) {
            logger.warn("类型转换失败: {} -> {}，使用原始值", value, targetType.getSimpleName());
            return value; // 转换失败时返回原始值
        }
    }*/
    /**
     * 从Map设置对象字段值
     */
    public static <T> void setFieldsFromMap(T config, Map<String, Object> configData,String mainPath) {

        Class<?> clazz = config.getClass();
        Map<String, Field> fieldMap = getAllField(clazz);
        for (Field field : fieldMap.values()) {

            // 只处理带@MiaoValue注解的字段
            if (!field.isAnnotationPresent(MiaoValue.class)) {continue;}
            logger.debug("处理字段: {} (类型: {})", field.getName(), field.getType());

            MiaoValue miaoValue = field.getAnnotation(MiaoValue.class);
            // 1. 确定字段的子路径（注解path优先，否则用字段名）
            String fieldSubPath = miaoValue.path().trim().isEmpty() ? field.getName() : miaoValue.path();
            // 2. 组合主路径和子路径，得到完整配置路径
            String fullConfigPath = buildFullPath(mainPath, fieldSubPath);
            // 3. 从配置数据中按完整路径获取值（支持嵌套路径）
            Object value = PathUtils.getValue(configData, fullConfigPath);


                field.setAccessible(true);
                if (value != null) {
                    try {
                        // 简单的类型转换
                            value = TypeConverter.convertValue(value, field.getType(),true);
                        field.set(config, value);
                    } catch (Exception e) {
                        logger.warn("设置{}字段错误，使用默认值: {}", field.getName(),
                                new MiaoConfigSetException(e.getMessage(), config.toString()).getMessage());
                    }
                }else{
                    logger.warn("实例[{}]的配置路径[{}]不存在，字段[{}]使用默认值",
                            config, fullConfigPath, field.getName());

                }
        }
    }
    public static <T> Map<String, Object> getMapForClazz(T config, Boolean excludeDisposable) {
        Map<String, Object> resultMap = new HashMap<>();
        Class<?> clazz = config.getClass();
        MiaoConfig miaoConfig = clazz.getAnnotation(MiaoConfig.class);
        if (miaoConfig == null) {
            return resultMap;
        }

        String mainPath = miaoConfig.path();  // 获取主节点路径
        Map<String, Field> fieldMap = getAllField(clazz);

        // 过滤一次性字段
        if (excludeDisposable) {
            fieldMap.values().removeIf(field -> {
                MiaoValue value = field.getAnnotation(MiaoValue.class);
                return value != null && value.disposable() == MiaoIsEnable.ENABLE;
            });
        }

        // 遍历字段生成配置Map（支持嵌套路径）
        for (Field field : fieldMap.values()) {
            MiaoValue miaoValue = field.getAnnotation(MiaoValue.class);
            if (miaoValue == null) {
                continue;
            }

            // 确定字段路径并构建完整路径
            String fieldPath = miaoValue.path().isEmpty() ? field.getName() : miaoValue.path();
            String fullPath = buildFullPath(mainPath, fieldPath);

            try {
                field.setAccessible(true);
                Object fieldValue = field.get(config);
                // 按完整路径设置嵌套值
                PathUtils.setValue(resultMap, fullPath, fieldValue);
            } catch (IllegalAccessException e) {
                logger.warn("获取字段{}值失败", field.getName(), e);
            }
        }

        return resultMap;
    }
    private void handleConfigError(String instance, String message, String configName, Exception e) {

        MiaoConfigReadException ex = new MiaoConfigReadException(message, configName);
        if (e != null) {
            logger.error("{}的{}: {}", instance, ex.getMessage(), e.getMessage());
        } else {
            logger.error("{}的{}", instance, ex.getMessage());
        }
    }
        /*
        public void writeToFile(String filename, String content) throws IOException {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
                // try-with-resources语法，自动关闭资源
                writer.write(content);
            }
        }*/


    /**
     * 获取当前存活的所有实例（排除已被回收的）
     */
    public List<T> getAliveInstances() {
        cleanUp();
        return container.stream()
                .map(WeakReference::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 从队列中获取被回收的引用，并从容器中删除
     */
    private void cleanUp() {
        Reference<? extends T> ref;
        Set<Reference<? extends T>> refsToRemove = new HashSet<>();

        // 收集所有需要移除的引用
        while ((ref = referenceQueue.poll()) != null) {
            refsToRemove.add(ref);
        }

        // 使用Set.contains更快
        if (!refsToRemove.isEmpty()) {
            container.removeIf(refsToRemove::contains);
        }
    }
}