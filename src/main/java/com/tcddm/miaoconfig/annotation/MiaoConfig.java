package com.tcddm.miaoconfig.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) // 必须设置为RUNTIME
@Target(ElementType.TYPE)
public @interface MiaoConfig {
    String configName() default "config";
    String path() default "";
}
