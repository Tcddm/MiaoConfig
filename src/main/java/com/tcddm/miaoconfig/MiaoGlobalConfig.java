package com.tcddm.miaoconfig;

import com.tcddm.miaoconfig.egg.MiaoLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiaoGlobalConfig {
    private final MiaoConfigFileManager.MiaoConfigFile miaoConfigFile;
    private final MiaoLogger logger = MiaoLogger.getLogger(MiaoGlobalConfig.class);
    private final Map<String,Object> dynamicConfig=new ConcurrentHashMap<>();

    public MiaoGlobalConfig(MiaoConfigFileManager.MiaoConfigFile miaoConfigFile) {
        this.miaoConfigFile = miaoConfigFile;
    }

    public MiaoConfigFileManager.MiaoConfigFile getMiaoConfigFile() {
        return miaoConfigFile;
    }

    /**
     * 根据路径获取配置值，并转换为目标类型
     * @param path 配置路径（如 "db.port"）
     * @param defaultValue 转换失败或值不存在时的默认值
     * @param targetType 目标类型
     * @return 转换后的配置值或默认值
     */
    public <T> T get(String path, T defaultValue, Class<T> targetType) {
        Object temp = PathUtils.getValue(miaoConfigFile.getConfig(), path);
        if (temp == null) {
            return defaultValue;
        }
        try {
            Object converted = TypeConverter.convertValue(temp, targetType, true);
            return targetType.cast(converted);
        } catch (Exception e) {
            logger.warn("配置路径[{}]类型转换失败，使用默认值", path, e);
            return defaultValue;
        }
    }

    /**
     * 获取字符串类型配置
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public String getString(String path, String defaultValue) {
        return get(path, defaultValue, String.class);
    }

    /**
     * 获取字符串类型配置（默认值为null）
     * @param path 配置路径
     * @return 配置值或null
     */
    public String getString(String path) {
        return get(path, null, String.class);
    }

    /**
     * 获取int类型配置
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public int getInt(String path, int defaultValue) {
        return get(path, defaultValue, int.class);
    }

    /**
     * 获取int类型配置（默认值为0）
     * @param path 配置路径
     * @return 配置值或0
     */
    public int getInt(String path) {
        return get(path, 0, int.class);
    }

    /**
     * 获取boolean类型配置
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return get(path, defaultValue, boolean.class);
    }

    /**
     * 获取boolean类型配置（默认值为false）
     * @param path 配置路径
     * @return 配置值或false
     */
    public boolean getBoolean(String path) {
        return get(path, false, boolean.class);
    }

    /**
     * 获取double类型配置
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public double getDouble(String path, double defaultValue) {
        return get(path, defaultValue, double.class);
    }

    /**
     * 获取double类型配置（默认值为0.0）
     * @param path 配置路径
     * @return 配置值或0.0
     */
    public double getDouble(String path) {
        return get(path, 0.0, double.class);
    }

    /**
     * 获取通用列表配置（默认值为空列表）
     * @param path 配置路径
     * @return 配置值或null
     */
    public List<?> getList(String path) {
        return getList(path, new ArrayList<>());
    }

    /**
     * 获取通用列表配置
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    @SuppressWarnings("unchecked")
    public List<?> getList(String path, List<?> defaultValue) {
        return get(path, defaultValue, List.class);
    }

    /**
     * 获取字符串列表配置（默认值为空列表）
     * @param path 配置路径
     * @return 配置值或空列表
     */
    public List<String> getStringList(String path) {
        return getStringList(path, new ArrayList<>());
    }

    /**
     * 获取字符串列表配置
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 配置值或默认值
     * @注意 列表元素需为字符串类型，否则可能抛出类型转换异常
     */
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path, List<String> defaultValue) {
        return get(path, defaultValue, List.class);
    }

    /**
     * 修改配置
     * @param path 配置路径
     * @param value 值
     */
    public void set(String path,Object value){
        if (path == null || path.trim().isEmpty()) {
            logger.warn("配置路径不能为空");
            return;
        }
        dynamicConfig.put(path, value);
    }

    /**
     * 取消设置
     * @param path 配置路径
     */
    public void cancelSet(String path){
        if (path == null || path.trim().isEmpty()) {
            logger.warn("配置路径不能为空");
            return;
        }
        dynamicConfig.remove(path);
    }

    /**
     * 取消所有设置
     */
    public void cancelAllSet(){
        dynamicConfig.clear();
    }

    /**
     * 获得要修改的配置
     * @return 要修改的配置
     */
    public Map<String, Object> getDynamicConfig() {
        return dynamicConfig;
    }
}
