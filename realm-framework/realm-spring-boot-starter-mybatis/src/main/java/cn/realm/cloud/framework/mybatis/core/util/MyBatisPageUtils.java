package cn.realm.cloud.framework.mybatis.core.util;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.framework.common.pojo.SortField;
import cn.realm.cloud.framework.common.pojo.SortablePageParam;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MyBatis-Plus 与 {@link PageParam} / {@link PageResult} 的转换工具。
 *
 * <p>本工具类集中处理三类转换：
 * <ul>
 *     <li>{@link PageParam} → MyBatis-Plus 的 {@link Page}</li>
 *     <li>MyBatis-Plus 的 {@link IPage} → {@link PageResult}</li>
 *     <li>{@link SortField} 列表 → {@link Page} 的 ORDER BY</li>
 * </ul>
 *
 * <p><b>职责边界：本类只做转换，不做校验。</b>
 * 参数合法性（{@code pageNo >= 1}、{@code pageSize >= 1} 且
 * {@code <= MAX_PAGE_SIZE}）由调用方负责：
 * <ul>
 *   <li>HTTP 入口：{@code PageParam} 上的 {@code @Min} / {@code @Max}
 *       配合 Controller 的 {@code @Valid} 拦截</li>
 *   <li>内部调用：Service 层在调用 {@code BaseMapperX.selectPage}
 *       前显式校验，抛出业务异常</li>
 * </ul>
 * 本类信任入参已合法。如果非法值传到本类，最终由 MyBatis-Plus
 * 或数据库抛出底层异常，交由全局异常处理器统一转换。
 *
 * <p><b>为什么只有 {@code addOrder(Page, List)} 一个排序方法：</b>
 * 排序只服务于分页查询。不分页查询（{@code mapper.selectList(wrapper)}）
 * 如果需要排序，调用方直接在 wrapper 上写 {@code orderByAsc/Desc}——
 * 这种场景的排序字段通常是固定的，不需要动态传入。分页查询的排序
 * 则可能来自 {@link SortablePageParam} 的动态解析，所以需要
 * {@link #addOrder(Page, List)} 在构造 {@code Page} 之后把排序补上。
 *
 * <p><b>安全边界（必须阅读）：</b>
 * MyBatis-Plus 的 {@code OrderItem} 底层将字段名<b>直接拼接</b>
 * 到 SQL 的 {@code ORDER BY} 子句，列名无法通过 {@code ?} 占位符参数化。
 * 因此，<b>本类不负责字段合法性校验</b>，所有传入的 {@link SortField}
 * 必须已经过业务层白名单校验。如果调用方传入未经校验的
 * {@code SortField}（例如直接来自 HTTP 参数），将产生 SQL 注入风险。
 *
 * @author QI Guang
 */
public final class MyBatisPageUtils {

    /**
     * 空排序列表的常量，避免调用方反复创建。
     *
     * <p>供 {@code BaseMapperX.selectPage(PageParam, Wrapper)} 这类
     * “不需要排序”的重载使用，避免每次调用都创建一个空的 {@code List}。
     */
    public static final List<SortField> EMPTY_SORTS = Collections.emptyList();

    private MyBatisPageUtils() {
    }

    // ==================== PageParam → Page ====================

    /**
     * 将 {@link PageParam} 转换为 MyBatis-Plus 的 {@link Page}（无排序）。
     *
     * <p><b>不做参数校验：</b>{@code pageNo} / {@code pageSize} 为
     * {@code null} 时按 {@link PageParam#DEFAULT_PAGE_NO} /
     * {@link PageParam#DEFAULT_PAGE_SIZE} 兜底。如果传入非法值
     * （{@code pageSize <= 0}），本方法原样构造 {@code Page}，
     * 由 MyBatis-Plus 分页插件在查询时决定行为——调用方应在调用前
     * 完成校验，不要把“参数是否合法”的决策留给本方法。
     *
     * @param param 分页参数，不能为 {@code null}（调用方负责保证非空）
     * @param <T>   实体类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> toPage(PageParam param) {
        return new Page<>(param.getPageNoOrDefault(), param.getPageSizeOrDefault());
    }

    /**
     * 将可排序的 {@link SortablePageParam} 转换为 MyBatis-Plus 的 {@link Page}。
     *
     * <p>与 {@link #toPage(PageParam)} 一样，不做参数校验。排序字段
     * 由 {@link SortablePageParam#parseSortFields(Set)} 根据白名单解析，
     * 白名单外的字段被静默忽略——这是“排序配置可能不完整”的正常降级，
     * 不是错误。
     *
     * @param param         分页 + 排序参数，不能为 {@code null}
     * @param allowedFields 允许排序的字段白名单
     * @param <T>           实体类型
     * @return MyBatis-Plus 分页对象（含排序）
     */
    public static <T> Page<T> toPage(SortablePageParam param, Set<String> allowedFields) {
        Page<T> page = new Page<>(param.getPageNoOrDefault(), param.getPageSizeOrDefault());
        addOrder(page, param.parseSortFields(allowedFields));
        return page;
    }

    // ==================== IPage → PageResult ====================

    /**
     * 将 MyBatis-Plus 的 {@link IPage} 转换为 {@link PageResult}。
     *
     * <p>{@code page} 为 {@code null} 时返回 {@link PageResult#empty()}——
     * 这不是校验，而是“无结果时返回空分页”的转换语义。调用方拿到
     * 空分页后仍可正常渲染“无数据”状态。
     *
     * @param page MyBatis-Plus 分页结果，{@code null} 时返回空分页
     * @param <T>  元素类型
     * @return PageResult 对象
     */
    public static <T> PageResult<T> toPageResult(IPage<T> page) {
        if (page == null) {
            return PageResult.empty();
        }
        return PageResult.of(
                page.getRecords(),
                page.getTotal(),
                page.getSize(),
                page.getCurrent()
        );
    }

    // ==================== SortField → Order by ====================

    /**
     * 将排序字段列表应用到 MyBatis-Plus 的 {@link Page} 对象上。
     *
     * <p>同一字段重复出现时，只保留首次出现的方向，
     * 避免生成 {@code ORDER BY name ASC, name DESC} 这类行为不确定的 SQL。
     *
     * <p>{@code page} 或 {@code sortFields} 为 {@code null}/空时直接返回，
     * 不添加任何排序——这不是校验，是“无排序可加”时的无操作。
     * 排序本来就是可选的。
     *
     * @param page       分页对象，可为 {@code null}（无操作返回）
     * @param sortFields 已校验的排序字段列表（可为空）
     * @param <T>        实体类型
     */
    public static <T> void addOrder(Page<T> page, List<SortField> sortFields) {
        if (page == null || sortFields == null || sortFields.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (SortField f : sortFields) {
            if (!seen.add(f.getField())) {
                continue;
            }
            page.addOrder(f.isAsc()
                    ? OrderItem.asc(f.getField())
                    : OrderItem.desc(f.getField()));
        }
    }
}
