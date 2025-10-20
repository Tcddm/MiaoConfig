package com.tcddm.miaoconfig.annotation;

import com.tcddm.miaoconfig.MiaoIsEnable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MiaoValue {
    /**
     * 配置节点，不填默认为字段名
     * @return 配置节点
     */
    String path() default "";
    /**
     * 是否是一次性的（只会加载，不会保存）
     * @return 是否是一次性的
     */
     MiaoIsEnable disposable() default MiaoIsEnable.DISABLE;
}
