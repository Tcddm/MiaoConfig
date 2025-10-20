package com.tcddm.miaoconfig.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MiaoConfig {
    /**
     * 配置名称
     * @return 配置名称
     */
    String configName() default "config";

    /**
     * 配置主节点
     * @return 配置主节点
     */
    String path() default "";
}
