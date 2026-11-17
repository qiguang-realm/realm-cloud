package cn.realm.cloud.framework.core.dto;

/**
 * 分页元数据。
 *
 * <p><b>设计依据：</b>对齐 Stripe 的游标分页模型（Cursor-based Pagination）。
 * 游标分页在深翻页时性能恒定（O(1)），且在数据插入/删除时不会出现重复或遗漏。
 *
 * @param nextCursor 下一页的游标；如果 {@code hasMore} 为 {@code false}，则为 {@code null}
 * @param hasMore    是否还有下一页
 * @param limit      本页请求的条数限制
 * @author QI Guang
 */
public record PaginationMeta(
        String nextCursor,
        boolean hasMore,
        int limit
) {
}
