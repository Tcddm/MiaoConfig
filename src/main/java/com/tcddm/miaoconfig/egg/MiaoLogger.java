package com.tcddm.miaoconfig.egg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiaoLogger {
    private final Logger logger;
    private static final String MIAO_PREFIX = "=^^= ";

    // 私有构造器，通过静态方法获取实例
    private MiaoLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    // 静态工厂方法
    public static MiaoLogger getLogger(Class<?> clazz) {
        return new MiaoLogger(clazz);
    }

    // 核心格式化方法，所有日志都通过这里处理
    private String formatMessage(String message) {
        // 可以在这里添加任意统一的格式化逻辑
        return MIAO_PREFIX + message;
    }

    // 信息级别日志
    public void info(String msg) {
        logger.info(formatMessage(msg));
    }

    public void info(String format, Object... arguments) {
        logger.info(formatMessage(format), arguments);
    }

    // 调试级别日志
    public void debug(String msg) {
        logger.debug(formatMessage(msg));
    }

    public void debug(String format, Object... arguments) {
        logger.debug(formatMessage(format), arguments);
    }

    // 警告级别日志
    public void warn(String msg) {
        logger.warn(formatMessage(msg));
    }

    public void warn(String format, Object... arguments) {
        logger.warn(formatMessage(format), arguments);
    }

    // 错误级别日志
    public void error(String msg) {
        logger.error(formatMessage(msg));
    }

    public void error(String format, Object... arguments) {
        logger.error(formatMessage(format), arguments);
    }

    // 错误级别日志（带异常）
    public void error(String msg, Throwable throwable) {
        logger.error(formatMessage(msg), throwable);
    }

    public String getMiaoMsg() {
        return MIAO_PREFIX.trim();
    }
}
