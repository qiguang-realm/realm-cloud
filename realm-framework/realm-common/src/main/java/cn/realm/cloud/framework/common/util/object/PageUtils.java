package cn.realm.cloud.framework.common.util.object;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.lang.func.LambdaUtil;
import cn.realm.cloud.framework.common.enums.OrderDirection;
import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.SortablePageParam;
import cn.realm.cloud.framework.common.pojo.SortingField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.Collections;

/**
 * {@link PageParam} 工具类，提供分页参数处理、排序字段构建等常用功能。
 *
 * @author QI Guang
 */
public class PageUtils {

    private static final Logger log = LoggerFactory.getLogger(PageUtils.class);

    private PageUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 计算 SQL 查询的起始偏移量（用于 LIMIT 子句）。
     *
     * @param pageParam 分页参数
     * @return 偏移量（从 0 开始），不分页时返回 0
     * @throws IllegalArgumentException 如果 pageParam 为 null
     */
    public static int getStart(PageParam pageParam) {
        Assert.notNull(pageParam, "pageParam must not be null");
        // 使用 long 计算避免溢出，然后转 int（实际业务中 pageNo 和 pageSize 不会太大）
        long start = (long) (pageParam.getPageNo() - 1) * pageParam.getPageSize();
        if (start > Integer.MAX_VALUE) {
            log.warn("getStart: computed offset {} exceeds Integer.MAX_VALUE, returning Integer.MAX_VALUE", start);
            return Integer.MAX_VALUE;
        }
        return (int) start;
    }

    /**
     * 构建排序字段（默认降序）。
     *
     * @param func 排序字段的 Lambda 表达式（例如 User::getCreateTime）
     * @param <T>  排序字段所属的类型
     * @return 排序字段对象
     * @throws IllegalArgumentException 如果 func 为 null 或无法解析字段名
     */
    public static <T> SortingField buildSortingField(Func1<T, ?> func) {
        return buildSortingField(func, OrderDirection.DESC);
    }

    /**
     * 构建排序字段（使用枚举指定方向）。
     *
     * @param func  排序字段的 Lambda 表达式
     * @param order 排序方向枚举
     * @param <T>   排序字段所属的类型
     * @return 排序字段对象
     */
    public static <T> SortingField buildSortingField(Func1<T, ?> func, OrderDirection order) {
        Assert.notNull(func, "func must not be null");
        Assert.notNull(order, "order must not be null");
        try {
            String fieldName = LambdaUtil.getFieldName(func);
            SortingField sortingField = new SortingField(fieldName, order);
            if (log.isTraceEnabled()) {
                log.trace("buildSortingField: field={}, order={}", fieldName, order.getCode());
            }
            return sortingField;
        } catch (Exception e) {
            log.error("buildSortingField: failed to get field name from lambda", e);
            throw new IllegalArgumentException("Failed to resolve field name from lambda", e);
        }
    }

    /**
     * 构建排序字段（兼容字符串方向，自动转换为枚举）。
     *
     * @param func  排序字段的 Lambda 表达式
     * @param order 排序类型字符串，仅支持 "asc" 或 "desc"（不区分大小写）
     * @param <T>   排序字段所属的类型
     * @return 排序字段对象
     * @throws IllegalArgumentException 如果 order 无效
     */
    public static <T> SortingField buildSortingField(Func1<T, ?> func, String order) {
        Assert.notNull(order, "order must not be null");
        OrderDirection direction = OrderDirection.fromCode(order);
        if (direction == null) {
            throw new IllegalArgumentException(
                    String.format("Invalid order: '%s'. Must be 'asc' or 'desc' (case insensitive)", order));
        }
        return buildSortingField(func, direction);
    }

    /**
     * 为 SortablePageParam 设置默认排序字段（如果当前未设置任何排序字段）。
     *
     * @param sortablePageParam 可排序分页参数（可为 null）
     * @param func              默认排序字段的 Lambda 表达式
     * @param <T>               排序字段所属的类型
     */
    public static <T> void buildDefaultSortingField(SortablePageParam sortablePageParam, Func1<T, ?> func) {
        if (sortablePageParam == null) {
            log.trace("buildDefaultSortingField: sortablePageParam is null, skip");
            return;
        }
        if (CollUtil.isEmpty(sortablePageParam.getSortingFields())) {
            try {
                SortingField defaultField = buildSortingField(func);
                sortablePageParam.setSortingFields(Collections.singletonList(defaultField));
                if (log.isDebugEnabled()) {
                    log.debug("buildDefaultSortingField: set default sorting field: {} {}",
                            defaultField.getField(), defaultField.getOrder().getCode());
                }
            } catch (IllegalArgumentException e) {
                log.warn("buildDefaultSortingField: failed to build default sorting field", e);
            }
        } else {
            log.trace("buildDefaultSortingField: sortingFields already exists, skip setting default");
        }
    }
}
