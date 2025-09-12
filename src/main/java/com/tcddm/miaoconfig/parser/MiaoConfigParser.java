package com.tcddm.miaoconfig.parser;

import java.util.Map;

/**
 * 配置解析抽象类
 */
public interface MiaoConfigParser {
    /**
     * 从字符串解析配置数据（返回Map而不是直接设置对象）
     * @return 解析后的Map
     */
    Map<String, Object> parse(String content) throws Exception;

    /**
     * 将配置数据序列化为字符串
     * @return 序列化后的字符串
     */
    String serialize(Map<String, Object> configData) throws Exception;

    /**
     * 支持的文件后缀
     * @return 支持的文件后缀
     */
    String[] supportedExtensions();
}
