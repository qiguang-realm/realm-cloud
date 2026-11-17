package cn.realm.cloud.framework.core.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 统一分页响应体。
 *
 * <p><b>设计定位：</b>这是 Direct Resource（直接返回资源）模式下，<b>唯一</b>使用
 * 轻量 envelope（信封）的场景。不要用统一包装器（如 ApiResponse）包裹所有的
 * 成功响应，但在返回资源列表时，需要一个结构同时承载“数据”和“分页信息”。
 *
 * <p><b>结构示例：</b>
 * <pre>{@code
 * {
 *   "data": [ { "id": "1", "name": "Alice" } ],
 *   "pagination": {
 *     "nextCursor": "cursor_abc",
 *     "hasMore": true,
 *     "limit": 20
 *   }
 * }
 * }</pre>
 *
 * <p><b>不可变性：</b>本类为只读 DTO，所有字段 {@code final}。
 *
 * @param <T> 资源数据类型
 * @author QI Guang
 */
public final class PageResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页的资源列表。
     */
    private final List<T> data;

    /**
     * 分页元数据。
     */
    private final PaginationMeta pagination;

    private PageResponse(List<T> data, PaginationMeta pagination) {
        this.data = Objects.requireNonNull(data, "data 不能为 null");
        this.pagination = Objects.requireNonNull(pagination, "pagination 不能为 null");
    }

    /**
     * 构造分页响应。
     *
     * @param data       当前页的资源列表
     * @param nextCursor 下一页游标（如果 {@code hasMore} 为 false，传 null）
     * @param hasMore    是否还有下一页
     * @param limit      本页条数限制
     * @param <T>        资源类型
     * @return {@link PageResponse} 实例
     */
    public static <T> PageResponse<T> of(List<T> data, String nextCursor, boolean hasMore, int limit) {
        return new PageResponse<>(data, new PaginationMeta(nextCursor, hasMore, limit));
    }

    /**
     * 构造分页响应（无下一页，常用于最后一页）。
     *
     * @param data  当前页的资源列表
     * @param limit 本页条数限制
     * @param <T>   资源类型
     * @return {@link PageResponse} 实例
     */
    public static <T> PageResponse<T> ofLastPage(List<T> data, int limit) {
        return new PageResponse<>(data, new PaginationMeta(null, false, limit));
    }

    /**
     * 构造空分页响应。
     *
     * @param limit 本页条数限制
     * @param <T>   资源类型
     * @return {@link PageResponse} 实例
     */
    public static <T> PageResponse<T> empty(int limit) {
        return new PageResponse<>(List.of(), new PaginationMeta(null, false, limit));
    }

    // ==================== Getter ====================

    public List<T> getData() {
        return data;
    }

    public PaginationMeta getPagination() {
        return pagination;
    }
}
