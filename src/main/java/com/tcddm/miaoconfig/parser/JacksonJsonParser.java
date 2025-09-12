package com.tcddm.miaoconfig.parser;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

// =^^= Jackson JSON 解析器 =^^=
public class JacksonJsonParser implements MiaoConfigParser {
    private final ObjectMapper mapper;

    public JacksonJsonParser() {
        this.mapper = new ObjectMapper();
        // 配置 Jackson 忽略未知属性
        this.mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public Map<String, Object> parse(String content) throws Exception {
        // 使用 Jackson 将 JSON 字符串解析为 Map
        return mapper.readValue(content, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    @Override
    public String serialize(Map<String, Object> configData) throws Exception {
        // 使用 Jackson 将 Map 序列化为格式化的 JSON 字符串
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configData);
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{".json"};
    }

}