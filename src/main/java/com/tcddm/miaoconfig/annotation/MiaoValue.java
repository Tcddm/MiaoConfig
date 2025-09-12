package com.tcddm.miaoconfig.annotation;

import com.tcddm.miaoconfig.MiaoIsEnable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) // 必须设置为RUNTIME
@Target(ElementType.FIELD)
public @interface MiaoValue {
    String path() default "";
    //是否是一次性的
     MiaoIsEnable disposable() default MiaoIsEnable.DISABLE;
}
