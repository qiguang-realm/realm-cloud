package cn.realm.cloud.framework.jpa.core.util;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.framework.common.pojo.SortablePageParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * Spring Data JPA 与 PageParam / PageResult 的转换工具。
 *
 * @author QI Guang
 */
public final class JpaPageUtils {

    private JpaPageUtils() {
    }

    /**
     * 将 PageParam 转换为 Spring Data 的 PageRequest（注意：Spring Data 页码从 0 开始）
     */
    public static PageRequest toPageRequest(PageParam param) {
        return PageRequest.of(param.getPageNum() - 1, param.getPageSize());
    }

    /**
     * 将 Spring Data 的 Page 转换为 PageResult
     */
    public static <T> PageResult<T> toPageResult(Page<T> page) {
        return PageResult.of(
                page.getContent(),
                page.getTotalElements(),
                page.getSize(),
                page.getNumber() + 1L  // Spring Data 从 0 开始，转为 1 开始
        );
    }

    /**
     * 将可排序的 PageParam 转换为 Spring Data 的 PageRequest。
     * <p>注意：Spring Data 页码从 0 开始，需将 pageNum 减 1。
     *
     * @param param         分页 + 排序参数
     * @param allowedFields 允许排序的字段白名单
     */
    public static PageRequest toPageRequest(SortablePageParam param, Set<String> allowedFields) {
        List<Sort.Order> orders = param.parseSortFields(allowedFields).stream()
                .map(f -> new Sort.Order(
                        f.isAsc() ? Sort.Direction.ASC : Sort.Direction.DESC,
                        f.getField()))
                .toList();
        return PageRequest.of(
                param.getPageNum() - 1,
                param.getPageSize(),
                orders.isEmpty() ? Sort.unsorted() : Sort.by(orders));
    }
}
