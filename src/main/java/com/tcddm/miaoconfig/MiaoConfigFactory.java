package com.tcddm.miaoconfig;

import com.tcddm.miaoconfig.annotation.MiaoValue;
import com.tcddm.miaoconfig.egg.MiaoLogger;
import com.tcddm.miaoconfig.exception.MiaoConfigReadException;
import com.tcddm.miaoconfig.exception.MiaoConfigSetException;
import com.tcddm.miaoconfig.parser.JacksonJsonParser;
import com.tcddm.miaoconfig.parser.MiaoConfigParser;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class MiaoConfigFactory {
    private static final MiaoLogger logger=MiaoLogger.getLogger(MiaoConfigFactory.class);
    private static final Map<String, MiaoConfigParser> PARSERS = new HashMap<>();
    private static final MiaoConfigFileManager miaoConfigFileManager =new MiaoConfigFileManager();
    private static final MiaoConfigClazzManager miaoConfigClazzManager =new MiaoConfigClazzManager();
    static {

        // 注册默认解析器
        registerParser(new JacksonJsonParser());
    }
    public static MiaoConfigClazzManager getConfigClazzManager(){return miaoConfigClazzManager;}
    public static class MiaoConfigClazzManager<T>{
        // 存储弱引用包装的实例（键：弱引用，值：实例本身，仅用于方便获取）
        private final CopyOnWriteArrayList<WeakReference<T>> container = new CopyOnWriteArrayList<>();
        // 引用队列：当弱引用关联的对象被回收时，弱引用会被加入此队列
        private final ReferenceQueue<T> referenceQueue = new ReferenceQueue<>();

        /**
         * 添加实例到容器
         */
        private void add(T instance) {
            // 清理已被回收的引用（每次添加时触发一次清理，也可单独线程定期清理）
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

            com.tcddm.miaoconfig.annotation.MiaoConfig miaoConfigAnnotation = instance.getClass().getAnnotation(com.tcddm.miaoconfig.annotation.MiaoConfig.class);
            String configName = miaoConfigAnnotation.configName();
            try {

                // 注入配置值
                MiaoConfigFactory.setFieldsFromMap(instance, getConfigData(configName));

                logger.info("配置注入完成: {}", instance.toString());
            } catch (IOException e) {
                handleConfigError(instance.toString(),"读取配置文件失败", configName, e);
            } catch (Exception e) {
                handleConfigError(instance.toString(),"解析配置文件失败", configName, e);
            }

            return this;
        }

        public void saveAllConfig(){

            for(T instance:getAliveInstances()){
                saveConfig(instance);
            }
        }
        private Map<String,Object> saveEdit(T instance,MiaoConfigFileManager.MiaoConfigFile miaoConfigFile){
            return miaoConfigFile.putConfigAndGet(getMapForClazz(instance,true));
        }
        public void saveConfig(T instance){
            if (!instance.getClass().isAnnotationPresent(com.tcddm.miaoconfig.annotation.MiaoConfig.class)) {
                logger.debug("实例缺少注解: {}", instance.getClass().getSimpleName());
                return;
            }
            com.tcddm.miaoconfig.annotation.MiaoConfig miaoConfigAnnotation = instance.getClass().getAnnotation(com.tcddm.miaoconfig.annotation.MiaoConfig.class);
            String configName = miaoConfigAnnotation.configName();
            try {
                // 获取配置文件
                MiaoConfigFileManager.MiaoConfigFile miaoConfigFile= MiaoConfigFactory.getConfigFileManager().getForName(configName);
                Path configPath = miaoConfigFile.getFilePath(); // 替换File为Path
                // NIO方式检查文件是否存在
                if (configPath == null || !Files.exists(configPath) || !Files.isRegularFile(configPath)) {
                    handleConfigError(instance.toString(), "配置文件不存在或不是常规文件", configName, null);
                    return;
                }
                //反序列化
                MiaoConfigParser miaoConfigParser = MiaoConfigFactory.getParser(configPath.getFileName().toString());
                Map<String,Object> configMap=saveEdit(instance,miaoConfigFile);
                //判断配置是否相同
                if(configMap.hashCode()==miaoConfigFile.getConfigHash()){
                    logger.info("配置保存完成,但是由于没有更改并未写入文件: {}",instance.toString());
                    return;
                }
                String temp= miaoConfigParser.serialize(configMap);
                //写入文件
                Files.write(
                        configPath,
                        temp.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE
                );
                //完成
                logger.info("配置保存完成: {}",instance.toString());

        }catch (IOException e) {
                handleConfigError(instance.toString(),"写入配置文件失败", configName, e);
            }
            catch (Exception e) {
                handleConfigError(instance.toString(),"保存配置文件失败", configName, e);
            }
        }

        private void handleConfigError(String instance,String message, String configName, Exception e) {

            MiaoConfigReadException ex = new MiaoConfigReadException(message, configName);
            if (e != null) {
                logger.error("{}的{}: {}",instance ,ex.getMessage(), e.getMessage());
            } else {
                logger.error("{}的{}",instance,ex.getMessage());
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
    public static MiaoConfigFileManager getConfigFileManager(){return miaoConfigFileManager;}
    public static class MiaoConfigFileManager{
        private  final Map<String,MiaoConfigFile> CONFIGS=new HashMap<>();

        public MiaoConfigFile getForName(String name){return CONFIGS.get(name);}
        public MiaoConfigFileManager addConfigFile(String name,Path path){
            if(ensureFileExists(path)){
                try {
                    CONFIGS.put(name,new MiaoConfigFile(path,getConfigData(path)));
                } catch (Exception e) {
                   logger.error("添加反序列化缓存失败: {}",e.getMessage());
                    CONFIGS.put(name,new MiaoConfigFile(path,new HashMap<>()));
                }
            }
            return this;
        }
        public MiaoConfigFileManager addConfigFile(String path) {
            // 直接使用NIO的Paths工具类创建Path对象
            Path configPath = Paths.get(path);
            String fileName = getFileNameWithoutExtension(configPath.getFileName().toString());
            return addConfigFile(fileName, configPath);
        }

        public MiaoConfigFileManager addConfigFile(String name, String path) {
            // 将路径字符串转换为Path对象
            Path configPath = Paths.get(path);
            return addConfigFile(name, configPath);
        }

        public MiaoConfigFileManager addConfigFile(File file) {
            // 将File转换为Path
            Path configPath = file.toPath();
            String fileName = getFileNameWithoutExtension(configPath.getFileName().toString());
            return addConfigFile(fileName, configPath);
        }
        public MiaoConfigFileManager addConfigFile(Path path) {
            addConfigFile(getFileNameWithoutExtension(path.getFileName().toString()),path);
            return this;
        }
        public MiaoConfigFileManager addConfigFilePath(String path) {
            // 使用NIO的Path替代File处理目录
            Path dirPath = Paths.get(path);
            List<Path> filePaths = new ArrayList<>();

            try {
                // 使用NIO的Files.walk()递归遍历目录（替代原有的getAllFiles()方法）
                // 最大深度设置为Integer.MAX_VALUE以遍历所有子目录
                Files.walk(dirPath, Integer.MAX_VALUE)
                        .forEach(filePaths::add);    // 收集所有文件路径

                // 遍历所有文件路径并添加配置
                for (Path filePath : filePaths) {
                    addConfigFile(filePath);
                }
            } catch (Exception e) {
                logger.error("添加配置文件夹失败: {}", e.getMessage());
            }

            return this;
        }
        /*private List<File> getAllFiles(File directory) throws MiaoConfigReadException{
            List<File> fileList = new ArrayList<>();

            // 检查目录是否存在
            if (!directory.exists()) {
                throw new MiaoConfigReadException("目录不存在" ,directory.getAbsolutePath());
            }

            // 检查是否是目录
            if (!directory.isDirectory()) {
                throw new MiaoConfigReadException("不是目录" ,directory.getAbsolutePath());
            }

            // 获取目录中的所有文件和子目录
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        // 如果是子目录，递归处理
                        fileList.addAll(getAllFiles(file));
                    } else {
                        // 如果是文件，添加到列表
                        fileList.add(file);
                    }
                }
            }

            return fileList;
        }*/
        private String getFileNameWithoutExtension(String fileName) {
            if (fileName == null || fileName.isEmpty()) {
                return fileName;
            }

            // 找到最后一个点的位置
            int lastDotIndex = fileName.lastIndexOf('.');

            // 如果没有点，或者点是最后一个字符，则返回原文件名
            if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
                return fileName;
            }

            // 截取从开头到最后一个点之前的部分
            return fileName.substring(0, lastDotIndex);
        }
        public static class MiaoConfigFile{
            private final Path filePath;
            private final Map<String,Object> config;
            private final int configHash;


            public MiaoConfigFile(Path filePath, Map<String, Object> config) {
                this.filePath = filePath;
                this.config = config;
                configHash =config.hashCode();
            }

            public int getConfigHash() {
                return configHash;
            }

            public Path getFilePath() {
                return filePath;
            }

            public Map<String, Object> getConfig() {
                return config;
            }

            public void putConfig(Map<String, Object> config) {
                this.config.putAll(config);
            }
            public Map<String,Object> putConfigAndGet(Map<String, Object> config) {
               putConfig(config);
               return this.config;
            }


            @Override
            public String toString() {
                return "MiaoConfigFile{" +
                        "filePath=" + filePath +
                        ", config=" + config +
                        ", configHash=" + configHash +
                        '}';
            }
        }
    }


    public static void registerParser(MiaoConfigParser parser) {
        for (String ext : parser.supportedExtensions()) {
            PARSERS.put(ext, parser);
        }
    }
    public static MiaoConfigParser getParser(String filename) throws MiaoConfigReadException {
        for (Map.Entry<String, MiaoConfigParser> entry : PARSERS.entrySet()) {
            if (filename.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        throw new MiaoConfigReadException("不支持的配置文件格式",filename);

    }
    public static <T> Map<String, Object> getMapForClazz(T config,Boolean excludeDisposable){


        Map<String, Object> resultMap = new HashMap<>();
        Class<?> clazz = config.getClass();
        Map<String, Field> fieldMap = getAllField(clazz);
                /*new HashMap<>();

        // 递归获取所有字段（当前类+父类）
        Class<?> currentClass = clazz;
        while (currentClass != null && !currentClass.equals(Object.class)) {
            // 获取当前类声明的所有字段
            Field[] declaredFields = currentClass.getDeclaredFields();
            for (Field field : declaredFields) {
                // 子类字段覆盖父类同名字段
                if (!fieldMap.containsKey(field.getName())) {
                    fieldMap.put(field.getName(), field);
                }
            }
            // 移动到父类
            currentClass = currentClass.getSuperclass();
        }*/
        if(excludeDisposable){
            fieldMap.values().removeIf(
                    field -> field.getAnnotation(MiaoValue.class)
                            .disposable()==MiaoIsEnable.ENABLE
            );
        }
        // 提取字段值到结果Map
        for (Map.Entry<String, Field> entry : fieldMap.entrySet()) {
            String fieldName = entry.getKey();
            Field field = entry.getValue();

            try {
                field.setAccessible(true); // 允许访问私有字段
                Object fieldValue = field.get(config);
                resultMap.put(fieldName, fieldValue);
            } catch (IllegalAccessException e) {
                logger.warn("获取{}字段值失败: {}",fieldName, e);

            }
        }

        return resultMap;
    }
    private static Map<String, Field> getAllField(Class clazz){
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
    /**
     * 从Map设置对象字段值
     */
    public static <T> void setFieldsFromMap(T config, Map<String, Object> configData) {

        Class<?> clazz = config.getClass();
        Map<String, Field> fieldMap = getAllField(clazz);

        for (Map.Entry<String, Object> entry : configData.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            Field field = fieldMap.get(fieldName);
            if (field != null) {
                field.setAccessible(true);

                // 简单的类型转换
                if (value != null && !field.getType().isInstance(value)) {
                    value = convertValue(value, field.getType());
                }

                if (value != null) {
                   try {
                           field.set(config, value);
                   }catch (Exception e){
                       logger.warn("设置{}字段错误，使用默认值: {}",field.getName(),
                               new MiaoConfigSetException(e.getMessage(),config.toString()).getMessage());
                   }
                }
            } else {
                // 字段不存在，记录警告但继续处理其他字段
                logger.warn("配置中存在未定义的字段: {}，已忽略", fieldName);
            }
        }
    }
    /**
     * 类型转换（安全版本）
     */
    private static Object convertValue(Object value, Class<?> targetType) {
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
    }

    private  static boolean ensureFileExists(Path filePath) {
        if (filePath == null) {
            return false;
        }
        // 用 Files.exists 替代 File.exists()
        if (Files.exists(filePath)) {
            return Files.isRegularFile(filePath); // 替代 File.isFile()
        }
        // 用 Path.getParent() 替代 File.getParentFile()
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                // 用 Files.createDirectories 替代 File.mkdirs()（支持创建多级目录）
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                logger.error("创建父目录失败: {}", e.getMessage());
                return false;
            }
        }
        // 用 Files.createFile 替代 File.createNewFile()
        try {
            Files.createFile(filePath);
            return true;
        } catch (IOException e) {
            logger.error("创建文件失败: {}", e.getMessage());
            return false;
        }
    }
    private static String readFileToString(Path path) throws IOException {
        // 使用NIO的Files工具类读取所有字节并转换为字符串
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> getConfigData(String configName) throws Exception {
        // 获取配置文件的Path对象（而非File）
        Path path = MiaoConfigFactory.getConfigFileManager().getForName(configName).getFilePath();
        return getConfigData(path);
    }

    private static Map<String, Object> getConfigData(Path path) throws Exception {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            // 使用NIO的Files.exists()检查文件是否存在
            throw new MiaoConfigReadException(path != null ? path.toString() : "null", "配置文件不存在或不是常规文件");
        }

        // 读取文件内容（使用NIO路径）
        String content = readFileToString(path);

        // 解析配置（使用文件名获取解析器）
        MiaoConfigParser miaoConfigParser = MiaoConfigFactory.getParser(path.getFileName().toString());


        return miaoConfigParser.parse(content);
    }
}
