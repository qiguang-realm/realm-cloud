package cn.realm.cloud.framework.jpa.core.util;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.framework.common.pojo.SortField;
import cn.realm.cloud.framework.common.pojo.SortablePageParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring Data JPA 与 {@link PageParam} / {@link PageResult} 的转换工具。
 *
 * <p><b>职责边界：本类只做转换，不做校验。</b>
 * 参数合法性（{@code pageNo >= 1}、{@code pageSize >= 1} 且
 * {@code <= MAX_PAGE_SIZE}）由调用方负责：
 * <ul>
 *   <li>HTTP 入口：{@code PageParam} 上的 {@code @Min} / {@code @Max}
 *       配合 Controller 的 {@code @Valid} 拦截</li>
 *   <li>内部调用：Service 层在调用本类前显式校验，抛出业务异常</li>
 * </ul>
 * 本类信任入参已合法。如果非法值传到本类，由 Spring Data 的
 * {@link PageRequest#of} 抛出底层 {@code IllegalArgumentException}，
 * 交由全局异常处理器统一转换。
 *
 * <p><b>与 {@code MyBatisPageUtils} 的异常行为差异：</b>
 * <ul>
 *   <li>JPA：{@code PageRequest.of(page, size)} 对 {@code page < 0}
 *       或 {@code size <= 0} <b>一定</b>抛 {@code IllegalArgumentException}——
 *       这是 Spring Data 自身的防御，不是本类的校验</li>
 *   <li>MyBatis-Plus：{@code new Page<>(pageNo, size)} 对 {@code size <= 0}
 *       的行为取决于版本（可能退化为全量查询或 {@code LIMIT 0}），
 *       不一定抛异常</li>
 * </ul>
 * 这意味着如果 Service 层漏了校验，两个 starter 暴露的错误形态不同——
 * 这正是“校验必须在 Service 层做”的原因：工具类兜不住时，底层行为
 * 不可预测。
 *
 * <p><b>字段命名语义（与 MyBatis-Plus 不同，必须注意）：</b>
 * Spring Data 的 {@link Sort.Order} 接收的是 <b>JPA 实体属性名</b>
 * （如 {@code createdAt}），由 Hibernate 根据 {@code @Column} 映射为数据库列名；
 * 而 MyBatis-Plus 的 {@code OrderItem} 接收的是 <b>数据库列名</b>
 * （如 {@code created_at}）。因此，{@link SortablePageParam#parseSortFields(Set)}
 * 的白名单在 JPA 场景下必须登记属性名，不能与 MyBatis 场景的列名白名单混用，
 * 否则运行时抛 {@code PropertyReferenceException}。
 *
 * <p><b>安全边界：</b>与 MyBatis-Plus 不同，Spring Data 的 {@code Sort}
 * 最终通过实体属性名解析为列名，不直接拼接 SQL 字符串，
 * 因此字段名注入的风险低于 MyBatis-Plus。但白名单仍然必要——
 * 它可以防止用户按敏感属性（如密码哈希）排序，也避免因属性名拼错导致
 * {@code PropertyReferenceException} 以 500 暴露给前端。
 *
 * @author QI Guang
 */
public final class JpaPageUtils {

    private JpaPageUtils() {
    }

    /**
     * 将 {@link PageParam} 转换为 Spring Data 的 {@link PageRequest}。
     *
     * <p>Spring Data 页码从 0 开始，因此将 {@code pageNo - 1} 作为页码。
     *
     * <p><b>不做参数校验：</b>{@code pageNo} / {@code pageSize} 为
     * {@code null} 时按 {@link PageParam#DEFAULT_PAGE_NO} /
     * {@link PageParam#DEFAULT_PAGE_SIZE} 兜底。如果传入非法值
     * （{@code pageNo <= 0} 或 {@code pageSize <= 0}），
     * {@link PageRequest#of} 会抛 {@code IllegalArgumentException}——
     * 调用方应在调用前完成校验，不要把“参数是否合法”的决策留给本方法。
     *
     * @param param 分页参数，不能为 {@code null}（调用方负责保证非空）
     * @return Spring Data 分页请求
     */
    public static PageRequest toPageRequest(PageParam param) {
        return PageRequest.of(param.getPageNoOrDefault() - 1, param.getPageSizeOrDefault());
    }

    /**
     * 将 {@link SortablePageParam} 转换为 Spring Data 的 {@link PageRequest}。
     *
     * <p>排序字段名必须是 JPA 实体属性名（见类注释）。
     * 同一字段重复出现时只保留首次出现的方向，避免生成
     * {@code ORDER BY name ASC, name DESC} 这类行为不确定的 SQL。
     *
     * <p>与 {@link #toPageRequest(PageParam)} 一样，不做参数校验。
     * 排序字段由 {@link SortablePageParam#parseSortFields(Set)} 根据白名单
     * 解析，白名单外的字段被静默忽略——这是“排序配置可能不完整”的正常降级，
     * 不是错误。
     *
     * @param param         分页 + 排序参数，不能为 {@code null}
     * @param allowedFields 允许排序的实体属性名白名单
     * @return Spring Data 分页请求
     */
    public static PageRequest toPageRequest(SortablePageParam param, Set<String> allowedFields) {
        List<SortField> sortFields = param.parseSortFields(allowedFields);
        Set<String> seen = new LinkedHashSet<>();
        List<Sort.Order> orders = sortFields.stream()
                .filter(f -> seen.add(f.getField()))
                .map(f -> new Sort.Order(
                        f.isAsc() ? Sort.Direction.ASC : Sort.Direction.DESC,
                        f.getField()))
                .toList();

        return PageRequest.of(
                param.getPageNoOrDefault() - 1,
                param.getPageSizeOrDefault(),
                orders.isEmpty() ? Sort.unsorted() : Sort.by(orders));
    }

    /**
     * 将 Spring Data 的 {@link Page} 转换为 {@link PageResult}。
     *
     * <p>Spring Data 页码从 0 开始，转换为 {@link PageResult} 的 1 起始页码。
     * {@code page} 为 {@code null} 时返回 {@link PageResult#empty()}——
     * 这不是校验，而是“无结果时返回空分页”的转换语义。
     *
     * @param page Spring Data 分页结果，{@code null} 时返回空分页
     * @param <T>  元素类型
     * @return PageResult 对象
     */
    public static <T> PageResult<T> toPageResult(Page<T> page) {
        if (page == null) {
            return PageResult.empty();
        }
        return PageResult.of(
                page.getContent(),
                page.getTotalElements(),
                page.getSize(),
                page.getNumber() + 1L  // Spring Data 从 0 开始，转为 1 开始
        );
    }
}
