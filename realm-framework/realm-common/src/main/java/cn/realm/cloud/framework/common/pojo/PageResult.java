package cn.realm.cloud.framework.common.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果（框架无关）。
 * <p>字段命名保持中性，可同时承载：
 * <ul>
 *     <li>MyBatis-Plus 的 {@code IPage<T>} 转换结果</li>
 *     <li>Spring Data JPA 的 {@code Page<T>} 转换结果</li>
 * </ul>
 * <p>本类位于 realm-common，不依赖任何持久层框架；具体转换方法由
 * {@code realm-spring-boot-starter-mybatis} 和 {@code realm-spring-boot-starter-data-jpa} 分别提供。
 *
 * @param <T> 列表元素类型
 * @author QI Guang
 */
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "分页响应结果")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------- 字段 ----------

    /**
     * 当前页的数据列表（不可为空，默认空列表）。
     * <p>对应 MyBatis-Plus 的 {@code getRecords()}、Spring Data 的 {@code getContent()}。
     */
    @Schema(description = "当前页数据列表",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> list = Collections.emptyList();

    /**
     * 总记录数。
     * <p>对应 MyBatis-Plus 的 {@code getTotal()}、Spring Data 的 {@code getTotalElements()}。
     */
    @Schema(description = "总记录数",
            example = "100",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long total;

    /**
     * 每页条数。
     * <p>对应 MyBatis-Plus 的 {@code getSize()}、Spring Data 的 {@code getSize()}。
     */
    @Schema(description = "每页条数",
            example = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long pageSize;

    /**
     * 当前页码（从 1 开始）。
     * <p>对应 MyBatis-Plus 的 {@code getCurrent()}、Spring Data 的 {@code getNumber() + 1}。
     */
    @Schema(description = "当前页码，从 1 开始",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long pageNum;

    /**
     * 总页数（自动计算）。
     * <p>对应 MyBatis-Plus 的 {@code getPages()}、Spring Data 的 {@code getTotalPages()}。
     */
    @Schema(description = "总页数",
            example = "10")
    @Setter(AccessLevel.NONE)
    private long pages;

    /**
     * 是否有上一页（自动计算）。
     */
    @Schema(description = "是否有上一页")
    @Setter(AccessLevel.NONE)
    private boolean hasPrevious;

    /**
     * 是否有下一页（自动计算）。
     */
    @Schema(description = "是否有下一页")
    @Setter(AccessLevel.NONE)
    private boolean hasNext;

    // ---------- 私有构造器 ----------

    private PageResult(List<T> list, long total, long pageSize, long pageNum,
                       long pages, boolean hasPrevious, boolean hasNext) {
        this.list = list != null ? list : Collections.emptyList();
        this.total = total;
        this.pageSize = pageSize;
        this.pageNum = pageNum;
        this.pages = pages;
        this.hasPrevious = hasPrevious;
        this.hasNext = hasNext;
    }

    // ---------- 静态工厂方法 ----------

    /**
     * 构造分页响应（自动计算总页数、是否有前后页）。
     *
     * @param list     当前页数据
     * @param total    总记录数
     * @param pageSize 每页条数
     * @param pageNum  当前页码（从 1 开始）
     * @param <T>      数据类型
     * @return PageResult 对象
     */
    public static <T> PageResult<T> of(List<T> list, long total, long pageSize, long pageNum) {
        long pages = calculateTotalPages(total, pageSize);
        boolean hasPrevious = pageNum > 1 && pageNum <= pages;
        boolean hasNext = pageNum < pages;
        return new PageResult<>(list, total, pageSize, pageNum, pages, hasPrevious, hasNext);
    }

    /**
     * 空分页响应（无数据，所有数值为 0）。
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0, 0, 0, 0, false, false);
    }

    // ---------- 便捷判断方法 ----------

    /**
     * 判断是否为第一页。
     */
    @JsonIgnore
    public boolean isFirstPage() {
        return this.pageNum == 1;
    }

    /**
     * 判断是否为最后一页。
     */
    @JsonIgnore
    public boolean isLastPage() {
        return this.pageNum == this.pages;
    }

    /**
     * 判断当前页是否有数据。
     */
    @JsonIgnore
    public boolean hasContent() {
        return this.list != null && !this.list.isEmpty();
    }

    // ---------- 私有辅助方法 ----------

    private static long calculateTotalPages(long total, long size) {
        if (size <= 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }
}
