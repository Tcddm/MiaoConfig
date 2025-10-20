package com.tcddm.miaoconfig;

import com.tcddm.miaoconfig.egg.MiaoLogger;
import com.tcddm.miaoconfig.exception.MiaoConfigReadException;
import com.tcddm.miaoconfig.exception.MiaoConfigSaveException;
import com.tcddm.miaoconfig.parser.MiaoConfigParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class MiaoConfigFileManager{
    private static final MiaoLogger logger=MiaoLogger.getLogger(MiaoConfigFileManager.class);
    private  final Map<String,MiaoConfigFile> CONFIGS=new ConcurrentHashMap<>();
    private final Map<String, Lock> fileLocks = new ConcurrentHashMap<>();
    public MiaoConfigFile getForName(String name){return CONFIGS.get(name);}
    public MiaoConfigFileManager addConfigFile(String name, Path path){
        if(ensureFileExists(path)){
            try {
                CONFIGS.put(name,new MiaoConfigFile(path,getConfigData(path)));
            } catch (Exception e) {
                logger.error("添加反序列化缓存失败: {}",e.getMessage());
                CONFIGS.put(name,new MiaoConfigFile(path,new ConcurrentHashMap<>()));
            }
        }
        return this;
    }
    public MiaoConfigFileManager addConfigFile(String path) {
        //创建Path
        Path configPath = Paths.get(path);
        String fileName = getFileNameWithoutExtension(configPath.getFileName().toString());
        return addConfigFile(fileName, configPath);
    }

    public MiaoConfigFileManager addConfigFile(String name, String path) {
        //将路径字符串转换为Path
        Path configPath = Paths.get(path);
        return addConfigFile(name, configPath);
    }

    public MiaoConfigFileManager addConfigFile(File file) {
        //将File转换为Path
        Path configPath = file.toPath();
        String fileName = getFileNameWithoutExtension(configPath.getFileName().toString());
        return addConfigFile(fileName, configPath);
    }
    public MiaoConfigFileManager addConfigFile(Path path) {
        addConfigFile(getFileNameWithoutExtension(path.getFileName().toString()),path);
        return this;
    }
    public MiaoConfigFileManager addConfigFilePath(String path) {
        //使用NIO的Path替代File处理目录
        Path dirPath = Paths.get(path);
        List<Path> filePaths = new ArrayList<>();

        try {
            //使用NIO的Files.walk()递归遍历目录（替代原有的getAllFiles()方法）
            //最大深度设置为Integer.MAX_VALUE以遍历所有子目录
            Files.walk(dirPath, Integer.MAX_VALUE)
                    .forEach(filePaths::add);    // 收集所有文件路径

            //遍历所有文件路径并添加配置
            for (Path filePath : filePaths) {
                addConfigFile(filePath);
            }
        } catch (Exception e) {
            logger.error("添加配置文件夹失败: {}", e.getMessage());
        }

        return this;
    }
    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }

        //找到最后一个点的位置
        int lastDotIndex = fileName.lastIndexOf('.');

        //如果没有点，或者点是最后一个字符，则返回原文件名
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return fileName;
        }

        //截取从开头到最后一个点之前的部分
        return fileName.substring(0, lastDotIndex);
    }
    private  static boolean ensureFileExists(Path filePath) {
        if (filePath == null) {
            return false;
        }
        //用Files.exists替代File.exists()
        if (Files.exists(filePath)) {
            return Files.isRegularFile(filePath); //替代File.isFile()
        }
        //用Path.getParent()替代File.getParentFile()
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                // 用Files.createDirectories替代File.mkdirs()（支持创建多级目录）
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                logger.error("创建父目录失败: {}", e.getMessage());
                return false;
            }
        }
        //用Files.createFile替代File.createNewFile()
        try {
            Files.createFile(filePath);
            return true;
        } catch (IOException e) {
            logger.error("创建文件失败: {}", e.getMessage());
            return false;
        }
    }
    private static String readFileToString(Path path) throws IOException {
        //使用NIO的Files工具类读取所有字节并转换为字符串
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    public static Map<String, Object> getConfigData(String configName) throws Exception {
        //获取配置文件的Path对象（而非File）
        Path path = MiaoConfigFactory.getConfigFileManager().getForName(configName).getFilePath();
        return getConfigData(path);
    }

    public static Map<String, Object> getConfigData(Path path) throws Exception {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            //使用NIO的Files.exists()检查文件是否存在
            throw new MiaoConfigReadException(path != null ? path.toString() : "null", "配置文件不存在或不是常规文件");
        }

        //读取文件内容（使用NIO路径）
        String content = readFileToString(path);

        //解析配置
        MiaoConfigParser miaoConfigParser = MiaoConfigFactory.getParser(path.getFileName().toString());


        return miaoConfigParser.parse(content);
    }
    public static class MiaoConfigFile{
        private final Path filePath;
        private final Map<String,Object> config;
        private boolean isEdit=false;


        public MiaoConfigFile(Path filePath, Map<String, Object> config) {
            this.filePath = filePath;
            this.config = config;

        }

        public boolean isEdit() {
            return isEdit;
        }
        public void setEdit(){isEdit=true;}
        public void cancelEdit(){isEdit=false;}
        public Path getFilePath() {
            return filePath;
        }

        public Map<String, Object> getConfig() {
            return config;
        }


        @Override
        public String toString() {
            return "MiaoConfigFile{" +
                    "filePath=" + filePath +
                    ", config=" + config +
                    ", isEdit=" + isEdit +
                    '}';
        }
    }
    public void reloadConfig(String configName,boolean isSave) {
        if (!CONFIGS.containsKey(configName)) {
            logger.warn("未找到对应配置文件：{}", configName);
            return;
        }
        Lock lock = fileLocks.computeIfAbsent(configName, k -> new ReentrantLock());
        lock.lock();
        MiaoConfigFile oldConfigFile=null;
        try {
            oldConfigFile = CONFIGS.get(configName);
            Path configPath = oldConfigFile.getFilePath();
            //先保存当前修改
            if(isSave){MiaoConfigFactory.getConfigClazzManager().saveConfig(configName);}
            //重新加载并添加新配置
            Map<String, Object> newConfigData = getConfigData(configPath);
            MiaoConfigFile newConfigFile = new MiaoConfigFile(configPath, newConfigData);
            //重置编辑状态
            newConfigFile.cancelEdit();
            CONFIGS.replace(configName,newConfigFile);
        } catch (Exception e) {
            handleConfigError(null, "重载配置文件失败", configName, e);
        } finally {
            lock.unlock();
        }
    }
    public void saveAllConfig(){
        for(String configName:CONFIGS.keySet()){
            saveConfig(configName);
        }
    }
    public void saveConfig(String configName) {saveConfig(configName,null);}
    public void saveConfig(String configName,String instanceName) {
        if(!CONFIGS.containsKey(configName)){
            logger.warn("未找到对应配置文件：{}",configName);
            return;
        }
        Lock lock = fileLocks.computeIfAbsent(configName, k -> new ReentrantLock());
        lock.lock();

        try {
            //获取配置文件
            MiaoConfigFileManager.MiaoConfigFile miaoConfigFile = CONFIGS.get(configName);
            Path configPath = miaoConfigFile.getFilePath(); // 替换File为Path
            //NIO方式检查文件是否存在
            if (configPath == null || !Files.exists(configPath) || !Files.isRegularFile(configPath)) {
                handleConfigError(null, "配置文件不存在或不是常规文件", configName, null);
                return;
            }
            //反序列化
            MiaoConfigParser miaoConfigParser = MiaoConfigFactory.getParser(configPath.getFileName().toString());
            Map<String, Object> configMap = CONFIGS.get(configName).getConfig();
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
            if(instanceName!=null){
                logger.info("配置保存完成: {}",instanceName);
            }else{logger.info("配置保存完成: {}",configName);}

        } catch (IOException e) {
            handleConfigError(instanceName, "写入配置文件失败", configName, e);
        } catch (Exception e) {
            handleConfigError(instanceName, "保存配置文件失败", configName, e);
        }finally {
            lock.unlock();
        }
    }
    private void handleConfigError(String instance, String message, String configName, Exception e) {

        MiaoConfigSaveException ex = new MiaoConfigSaveException(message, configName);
        if (e != null) {
            logger.error("{}的{}: {}", instance, ex.getMessage(), e.getMessage());
        } else {
            logger.error("{}的{}", instance, ex.getMessage());
        }
    }
}