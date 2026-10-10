package cn.realm.cloud.framework.mybatis.core.util;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.framework.common.pojo.SortField;
import cn.realm.cloud.framework.common.pojo.SortablePageParam;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * MyBatis-Plus 与 {@link PageParam} / {@link PageResult} 的转换工具。
 *
 * <p>本工具类集中处理三类转换：
 * <ul>
 *     <li>{@link PageParam} → MyBatis-Plus 的 {@link Page}</li>
 *     <li>MyBatis-Plus 的 {@link IPage} → {@link PageResult}</li>
 *     <li>{@link SortField} 列表 → Page / Wrapper 的 ORDER BY</li>
 * </ul>
 *
 * <p><b>安全说明：</b>排序字段必须在业务层通过白名单校验后传入，
 * 本工具类不负责字段合法性校验。
 *
 * @author QI Guang
 */
public final class MyBatisPageUtils {

    private MyBatisPageUtils() {
    }

    // ==================== PageParam → Page ====================

    /**
     * 将 {@link PageParam} 转换为 MyBatis-Plus 的 {@link Page}（无排序）。
     *
     * @param param 分页参数
     * @param <T>   实体类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> toPage(PageParam param) {
        return new Page<>(param.getPageNum(), param.getPageSize());
    }

    /**
     * 将可排序的 {@link SortablePageParam} 转换为 MyBatis-Plus 的 {@link Page}。
     *
     * @param param         分页 + 排序参数
     * @param allowedFields 允许排序的字段白名单
     * @param <T>           实体类型
     * @return MyBatis-Plus 分页对象（含排序）
     */
    public static <T> Page<T> toPage(SortablePageParam param, Set<String> allowedFields) {
        Page<T> page = new Page<>(param.getPageNum(), param.getPageSize());
        addOrder(page, param.parseSortFields(allowedFields));
        return page;
    }

    // ==================== IPage → PageResult ====================

    /**
     * 将 MyBatis-Plus 的 {@link IPage} 转换为 {@link PageResult}。
     *
     * @param page MyBatis-Plus 分页结果
     * @param <T>  元素类型
     * @return PageResult 对象
     */
    public static <T> PageResult<T> toPageResult(IPage<T> page) {
        return PageResult.of(
                page.getRecords(),
                page.getTotal(),
                page.getSize(),
                page.getCurrent()
        );
    }

    // ==================== SortField → Order by ====================

    /**
     * 将排序字段列表应用到 MyBatis-Plus 的 {@link Page} 对象上（用于分页场景）。
     *
     * @param page       分页对象
     * @param sortFields 已校验的排序字段列表（可为空）
     * @param <T>        实体类型
     */
    public static <T> void addOrder(Page<T> page, List<SortField> sortFields) {
        if (sortFields == null || sortFields.isEmpty()) {
            return;
        }
        for (SortField f : sortFields) {
            page.addOrder(f.isAsc()
                    ? OrderItem.asc(f.getField())
                    : OrderItem.desc(f.getField()));
        }
    }

    /**
     * 将排序字段列表应用到 MyBatis-Plus 的 {@link Wrapper} 上（用于不分页场景）。
     *
     * <p>仅支持 {@link AbstractWrapper} 的子类（如 {@code QueryWrapper}、{@code LambdaQueryWrapper}）。
     * 其他类型的 Wrapper（如自定义 Wrapper）会静默忽略排序。
     *
     * <p><b>注意：</b>{@link SortField#getField()} 在此处作为 <b>数据库列名</b> 使用，
     * 请确保白名单中登记的是列名而非 Java 属性名。
     *
     * @param wrapper    查询条件包装器
     * @param sortFields 已校验的排序字段列表（可为空）
     * @param <T>        实体类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> void addOrder(Wrapper<T> wrapper, List<SortField> sortFields) {
        if (sortFields == null || sortFields.isEmpty() || wrapper == null) {
            return;
        }
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?>)) {
            return;
        }
        AbstractWrapper rawWrapper = (AbstractWrapper) wrapper;
        for (SortField f : sortFields) {
            if (f.isAsc()) {
                rawWrapper.orderByAsc(f.getField());
            } else {
                rawWrapper.orderByDesc(f.getField());
            }
        }
    }

    /**
     * 空排序列表的常量，避免调用方反复创建。
     */
    public static final List<SortField> EMPTY_SORTS = Collections.emptyList();
}
