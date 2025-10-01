package com.tcddm.miaoconfig;

import com.tcddm.miaoconfig.egg.MiaoLogger;
import com.tcddm.miaoconfig.exception.MiaoConfigReadException;
import com.tcddm.miaoconfig.parser.JacksonJsonParser;
import com.tcddm.miaoconfig.parser.MiaoConfigParser;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MiaoConfigFactory {
    private static final MiaoLogger logger = MiaoLogger.getLogger(MiaoConfigFactory.class);
    private static final Map<String, MiaoConfigParser> PARSERS = new HashMap<>();
    private static final MiaoConfigFileManager miaoConfigFileManager = new MiaoConfigFileManager();
    private static final MiaoConfigClazzManager miaoConfigClazzManager = new MiaoConfigClazzManager();

    static {

        // 注册默认解析器
        registerParser(new JacksonJsonParser());
    }

    public static MiaoConfigClazzManager getConfigClazzManager() {
        return miaoConfigClazzManager;
    }



    public static MiaoConfigFileManager getConfigFileManager() {
        return miaoConfigFileManager;
    }


    public static void registerParser(MiaoConfigParser parser) {
        for (String ext : parser.supportedExtensions()) {
            PARSERS.put(ext, parser);
        }
        logger.debug("已注册，支持的文件后缀为{}", Arrays.toString(parser.supportedExtensions()));
    }

    public static MiaoConfigParser getParser(String filename) throws MiaoConfigReadException {
        for (Map.Entry<String, MiaoConfigParser> entry : PARSERS.entrySet()) {
            if (filename.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        throw new MiaoConfigReadException("不支持的配置文件格式", filename);

    }
}
