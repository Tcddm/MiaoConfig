package com.tcddm.miaoconfig.exception;


/**
 * 配置文件读取时的非严重异常
 * 用于表示配置读取过程中出现的问题，但不影响程序继续运行
 */
public class MiaoConfigReadException extends RuntimeException {
    private final String configPath;
    private final String detailMessage;

    public MiaoConfigReadException(String message, String configPath) {
        super("配置读取异常: " + message + " [文件: " + configPath + "]");
        this.configPath = configPath;
        this.detailMessage = message;
    }

    public MiaoConfigReadException(String message, String configPath, Throwable cause) {
        super("配置读取异常: " + message + " [文件: " + configPath + "]", cause);
        this.configPath = configPath;
        this.detailMessage = message;
    }

    //获取配置文件路径
    public String getConfigPath() {
        return configPath;
    }

    //获取详细信息（不含前缀和文件路径）
    public String getDetailMessage() {
        return detailMessage;
    }



}
