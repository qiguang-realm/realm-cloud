package cn.realm.cloud.framework.security.operatelog.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解。
 *
 * <p>依据：ASVS V7 要求所有认证决策记录日志。
 * 本注解标记需要记录操作日志的方法，切面自动注入操作人信息。
 *
 * @author QI Guang
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperateLog {

    /**
     * 操作类型（如 CREATE / UPDATE / DELETE）。
     */
    String type() default "";

    /**
     * 操作描述。
     */
    String description() default "";
}
