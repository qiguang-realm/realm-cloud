package cn.realm.cloud.framework.common.base.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应封装，用于返回分页列表数据。
 * <p>与 MyBatis Plus 的 {@code Page} 对象兼容，可通过工厂方法进行转换。
 * 建议使用 {@link #of(List, long, long, long)} 构造，以确保所有派生字段（总页数、是否有前后页）自动计算。
 *
 * @param <T> 列表元素类型
 * @author QI Guang
 */
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // 供序列化框架使用
public class PageResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页的数据列表（不可为空，默认空列表）
     */
    private List<T> records = Collections.emptyList();

    /**
     * 总记录数
     */
    private long total;

    /**
     * 每页显示条数
     */
    private long size;

    /**
     * 当前页码（从1开始）
     */
    private long current;

    /**
     * 总页数（自动计算）
     */
    @Setter(AccessLevel.NONE)  // 禁止外部直接修改，由工厂方法计算
    private long pages;

    /**
     * 是否有上一页（自动计算）
     */
    @Setter(AccessLevel.NONE)
    private boolean hasPrevious;

    /**
     * 是否有下一页（自动计算）
     */
    @Setter(AccessLevel.NONE)
    private boolean hasNext;

    // ---------- 私有构造器 ----------

    private PageResponse(List<T> records, long total, long size, long current, long pages,
                         boolean hasPrevious, boolean hasNext) {
        this.records = records != null ? records : Collections.emptyList();
        this.total = total;
        this.size = size;
        this.current = current;
        this.pages = pages;
        this.hasPrevious = hasPrevious;
        this.hasNext = hasNext;
    }

    // ---------- 静态工厂方法 ----------

    /**
     * 构造分页响应（自动计算总页数、是否有前后页）
     *
     * @param records 当前页数据
     * @param total   总记录数
     * @param size    每页条数
     * @param current 当前页码（从1开始）
     * @param <T>     数据类型
     * @return PageResponse 对象
     */
    public static <T> PageResponse<T> of(List<T> records, long total, long size, long current) {
        long pages = calculateTotalPages(total, size);
        boolean hasPrevious = current > 1 && current <= pages;
        boolean hasNext = current < pages;
        return new PageResponse<>(records, total, size, current, pages, hasPrevious, hasNext);
    }

    /**
     * 空分页响应（无数据，所有数值为0）
     *
     * @param <T> 数据类型
     * @return 空分页对象
     */
    public static <T> PageResponse<T> empty() {
        return new PageResponse<>(Collections.emptyList(), 0, 0, 0, 0, false, false);
    }

    // ---------- 便捷判断方法 ----------

    /**
     * 判断是否为第一页
     */
    @JsonIgnore
    public boolean isFirstPage() {
        return this.current == 1;
    }

    /**
     * 判断是否为最后一页
     */
    @JsonIgnore
    public boolean isLastPage() {
        return this.current == this.pages;
    }

    /**
     * 判断当前页是否有数据
     */
    @JsonIgnore
    public boolean hasContent() {
        return this.records != null && !this.records.isEmpty();
    }

    // ---------- 私有辅助方法 ----------

    /**
     * 计算总页数
     */
    private static long calculateTotalPages(long total, long size) {
        if (size <= 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }

    // ---------- 与 MyBatis Plus 的转换（可选） ----------

    /**
     * 从 MyBatis Plus Page 对象构建分页响应（需要引入 mybatis-plus 依赖）
     * <p>使用示例：{@code PageResponse.from(page)}
     *
     * @param page MyBatis Plus 的 Page 对象
     * @param <T>  数据类型
     * @return PageResponse 对象
     */
    /*
    public static <T> PageResponse<T> from(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        return PageResponse.of(page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent());
    }
    */
}
