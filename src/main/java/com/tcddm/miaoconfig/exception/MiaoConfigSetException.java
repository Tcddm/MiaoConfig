package com.tcddm.miaoconfig.exception;


/**
 * 注入字段时的非严重异常
 * 用于表示注入字段出现的问题，但不影响程序继续运行
 */
public class MiaoConfigSetException extends RuntimeException {
    private final String clazz;
    private final String detailMessage;

    public MiaoConfigSetException(String message, String clazz) {
        super("配置设置异常: " + message + " [类: " + clazz + "]");
        this.clazz = clazz;
        this.detailMessage = message;
    }


    // 获取配置文件路径
    public String getClazz() {
        return clazz;
    }

    // 获取详细信息（不含前缀和文件路径）
    public String getDetailMessage() {
        return detailMessage;
    }



}
