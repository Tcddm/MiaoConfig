package com.tcddm.miaoconfig;

import com.tcddm.miaoconfig.egg.MiaoLogger;
import com.tcddm.miaoconfig.exception.MiaoConfigReadException;
import com.tcddm.miaoconfig.parser.MiaoConfigParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MiaoConfigFileManager{
    private static final MiaoLogger logger=MiaoLogger.getLogger(MiaoConfigFileManager.class);
    private  final Map<String,MiaoConfigFile> CONFIGS=new HashMap<>();

    public MiaoConfigFile getForName(String name){return CONFIGS.get(name);}
    public MiaoConfigFileManager addConfigFile(String name, Path path){
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

    public static Map<String, Object> getConfigData(String configName) throws Exception {
        // 获取配置文件的Path对象（而非File）
        Path path = MiaoConfigFactory.getConfigFileManager().getForName(configName).getFilePath();
        return getConfigData(path);
    }

    public static Map<String, Object> getConfigData(Path path) throws Exception {
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