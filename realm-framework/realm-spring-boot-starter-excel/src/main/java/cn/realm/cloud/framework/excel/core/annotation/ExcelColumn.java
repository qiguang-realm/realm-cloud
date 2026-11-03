package cn.realm.cloud.framework.excel.core.annotation;

import java.lang.annotation.*;

/**
 * 标记 Java 字段与 Excel 列头的映射关系。
 *
 * @author QI Guang
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelColumn {

    /**
     * 列头名称。
     */
    String value();

    /**
     * 列顺序，值越小越靠前。
     */
    int order() default Integer.MAX_VALUE;
}
