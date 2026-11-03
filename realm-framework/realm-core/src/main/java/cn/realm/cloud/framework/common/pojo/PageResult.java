package cn.realm.cloud.framework.common.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果（框架无关）。
 *
 * <p>字段命名保持中性，可同时承载 MyBatis-Plus 的 {@code IPage<T>}
 * 与 Spring Data 的 {@code Page<T>} 的转换结果。
 *
 * <p>本类位于 realm-core，不依赖任何持久层框架；具体转换方法由
 * {@code realm-spring-boot-starter-mybatis} 和
 * {@code realm-spring-boot-starter-data-jpa} 分别提供。
 *
 * <p>响应对象创建后不可变：所有字段只在私有构造器中赋值，
 * 外部通过 {@link #of} / {@link #empty} 创建。
 *
 * @param <T> 列表元素类型
 * @author QI Guang
 */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "分页响应结果")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------- 字段 ----------

    /**
     * 当前页的数据列表（不可为空，默认空列表）。
     */
    @Schema(description = "当前页数据列表",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> list = Collections.emptyList();

    /**
     * 总记录数。
     */
    @Schema(description = "总记录数",
            example = "100",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long total;

    /**
     * 每页条数。
     */
    @Schema(description = "每页条数",
            example = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long pageSize;

    /**
     * 当前页码（从 1 开始）。
     */
    @Schema(description = "当前页码，从 1 开始",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long pageNo;

    /**
     * 总页数（自动计算）。
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

    private PageResult(List<T> list, long total, long pageSize, long pageNo,
                       long pages, boolean hasPrevious, boolean hasNext) {
        this.list = list != null ? list : Collections.emptyList();
        this.total = total;
        this.pageSize = pageSize;
        this.pageNo = pageNo;
        this.pages = pages;
        this.hasPrevious = hasPrevious;
        this.hasNext = hasNext;
    }

    // ---------- 静态工厂方法 ----------

    /**
     * 构造分页响应（自动计算总页数、是否有前后页）。
     */
    public static <T> PageResult<T> of(List<T> list, long total,
                                       long pageSize, long pageNo) {
        long pages = calculateTotalPages(total, pageSize);
        boolean hasPrevious = pageNo > 1 && pageNo <= pages;
        boolean hasNext = pageNo < pages;
        return new PageResult<>(list, total, pageSize, pageNo,
                pages, hasPrevious, hasNext);
    }

    /**
     * 按 {@link PageParam} 构造分页响应。
     *
     * <p>适用于 Service 层返回 {@code List + total} 的场景，
     * 避免调用方手动拆解 {@code getPageNoOrDefault()} /
     * {@code getPageSizeOrDefault()}，也避免参数顺序传错。
     *
     * @param param 分页请求参数，{@code null} 时按默认页码/页大小处理
     * @param list  当前页数据
     * @param total 总记录数
     */
    public static <T> PageResult<T> of(PageParam param, List<T> list, long total) {
        int pageNo = param != null ? param.getPageNoOrDefault()
                : PageParam.DEFAULT_PAGE_NO;
        int pageSize = param != null ? param.getPageSizeOrDefault()
                : PageParam.DEFAULT_PAGE_SIZE;
        return of(list, total, pageSize, pageNo);
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
        return this.pageNo == 1;
    }

    /**
     * 判断是否为最后一页。
     *
     * <p>使用 {@code >=} 而非 {@code ==}：总记录数为 0 时
     * {@code pages = 0}、{@code pageNo = 1}，此时唯一一页即为最后一页；
     * 同时兼容页码越界的边界情况。
     */
    @JsonIgnore
    public boolean isLastPage() {
        return this.pageNo >= this.pages;
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
