package cn.realm.cloud.framework.mybatis.core.mapper;

import cn.hutool.core.collection.CollUtil;
import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.framework.common.pojo.SortField;
import cn.realm.cloud.framework.common.pojo.SortablePageParam;
import cn.realm.cloud.framework.mybatis.core.util.MyBatisPageUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.github.yulichang.base.MPJBaseMapper;
import com.github.yulichang.interfaces.MPJBaseJoin;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 在 MyBatis-Plus 的 {@link BaseMapper} 基础上扩展，提供更丰富的分页、连表、批量操作能力。
 *
 * <p>本接口整合：
 * <ul>
 *     <li>{@link BaseMapper}：MyBatis-Plus 基础 CRUD 能力</li>
 *     <li>{@link MPJBaseMapper}：MyBatis-Plus-Join 连表查询能力</li>
 * </ul>
 *
 * <p><b>职责边界：本接口只做转换和委托，不做参数校验。</b>
 * {@code pageSize} 的取值范围由调用方（Service 层）保证：
 * HTTP 入口由 {@code PageParam} 上的 {@code @Min} / {@code @Max}
 * 配合 {@code @Valid} 拦截；内部调用由 Service 层显式校验。
 * 本接口信任入参已合法。如果非法值传到本接口，由 MyBatis-Plus
 * 或数据库抛出底层异常，交由全局异常处理器统一转换。
 *
 * <p><b>为什么不支持“不分页”模式：</b>依据 Google AIP-158 与
 * Microsoft REST API Guidelines——集合端点必须分页，不存在
 * “返回全量”这一选项。全量数据的需求应由独立的导出端点承担，
 * 而不是让列表查询通过 {@code pageSize = -1} 退化为全表扫描。
 * 因此所有分页方法都走 {@link MyBatisPageUtils#toPage(PageParam)}
 * 构造 {@code Page}，不再有“判断是否分页”的分支。
 *
 * <p><b>安全说明（CWE-89 / OWASP ASVS 5.3.4）：</b>
 * 所有涉及字段名的方法，均提供 {@code String} 与 {@code SFunction} 两个版本。
 * {@code String} 版本因存在 SQL 注入风险，已标记 {@link Deprecated}；
 * 新代码必须使用 Lambda 版本。排序字段必须通过 {@code allowedFields}
 * 白名单传入，白名单解析由 {@link SortablePageParam#parseSortFields(Set)}
 * 完成，本接口不负责字段合法性校验。
 *
 * <p><b>跨数据库兼容性：</b>
 * {@link #selectOneForUpdate} 与 {@link #selectFirstForUpdate} 依赖
 * {@code FOR UPDATE} 语法，<b>仅支持 MySQL / PostgreSQL / Oracle / 达梦 / 金仓</b>；
 * SQL Server 需在子接口中 override。
 *
 * @param <T> 实体类型
 * @author QI Guang
 */
public interface BaseMapperX<T> extends MPJBaseMapper<T> {

    /**
     * 查询首条记录时的固定分页大小。
     */
    int FIRST_PAGE_SIZE = 1;

    // ==================== 分页查询 ====================

    /**
     * 分页查询（无排序）。
     *
     * <p>纯委托：把空排序列表传给核心实现，避免每次调用都创建空集合。
     *
     * @param pageParam    分页参数
     * @param queryWrapper 查询条件
     * @return 分页结果
     */
    default PageResult<T> selectPage(PageParam pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, MyBatisPageUtils.EMPTY_SORTS, queryWrapper);
    }

    /**
     * 分页查询（带排序，白名单强校验）。
     *
     * <p>排序字段由 {@link SortablePageParam#parseSortFields(Set)} 根据
     * 白名单解析，白名单外的字段被静默忽略——这是“排序配置可能不完整”
     * 的正常降级，不是错误。本接口不重复校验字段合法性。
     *
     * @param pageParam     分页 + 排序参数
     * @param allowedFields 允许排序的字段白名单（数据库列名）
     * @param queryWrapper  查询条件
     * @return 分页结果
     */
    default PageResult<T> selectPage(SortablePageParam pageParam, Set<String> allowedFields,
                                     @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, pageParam.parseSortFields(allowedFields), queryWrapper);
    }

    /**
     * 分页查询（核心实现）。
     *
     * <p>四步纯转换：
     * <ol>
     *   <li>{@link MyBatisPageUtils#toPage(PageParam)} 构造 {@code Page}</li>
     *   <li>{@link MyBatisPageUtils#addOrder(Page, List)} 追加排序</li>
     *   <li>{@code selectPage(page, wrapper)} 执行查询</li>
     *   <li>{@link MyBatisPageUtils#toPageResult(IPage)} 转换为框架无关的分页结果</li>
     * </ol>
     *
     * <p><b>没有 {@code !pageParam.isPaged()} 分支：</b>“不分页”模式已废弃，
     * 列表 API 永远分页。全量查询走 {@code selectList(wrapper)} + 独立导出端点，
     * 不经过本方法。
     *
     * @param pageParam    分页参数
     * @param sortFields   已校验的排序字段列表
     * @param queryWrapper 查询条件
     * @return 分页结果
     */
    default PageResult<T> selectPage(PageParam pageParam, List<SortField> sortFields,
                                     @Param("ew") Wrapper<T> queryWrapper) {
        Page<T> page = MyBatisPageUtils.toPage(pageParam);
        MyBatisPageUtils.addOrder(page, sortFields);
        selectPage(page, queryWrapper);
        return MyBatisPageUtils.toPageResult(page);
    }

    // ==================== 连表分页查询 ====================

    /**
     * 连表分页查询（无排序）。
     *
     * @param pageParam     分页参数
     * @param clazz         结果类型
     * @param lambdaWrapper 连表查询条件
     * @return 分页结果
     */
    default <D> PageResult<D> selectJoinPage(PageParam pageParam, Class<D> clazz,
                                             MPJLambdaWrapper<T> lambdaWrapper) {
        Page<D> page = MyBatisPageUtils.toPage(pageParam);
        IPage<D> result = selectJoinPage(page, clazz, lambdaWrapper);
        return MyBatisPageUtils.toPageResult(result);
    }

    /**
     * 连表分页查询（带排序，白名单强校验）。
     *
     * <p>排序字段由 {@link SortablePageParam#parseSortFields(Set)} 解析。
     * 注意 {@link MyBatisPageUtils#addOrder(Page, List)} 接收的是
     * <b>数据库列名</b>，与 JPA 场景的“实体属性名”语义不同。
     *
     * @param pageParam     分页 + 排序参数
     * @param allowedFields 允许排序的字段白名单（数据库列名）
     * @param clazz         结果类型
     * @param lambdaWrapper 连表查询条件
     * @return 分页结果
     */
    default <D> PageResult<D> selectJoinPage(SortablePageParam pageParam, Set<String> allowedFields,
                                             Class<D> clazz, MPJLambdaWrapper<T> lambdaWrapper) {
        List<SortField> sortFields = pageParam.parseSortFields(allowedFields);
        Page<D> page = MyBatisPageUtils.toPage(pageParam);
        MyBatisPageUtils.addOrder(page, sortFields);
        IPage<D> result = selectJoinPage(page, clazz, lambdaWrapper);
        return MyBatisPageUtils.toPageResult(result);
    }

    /**
     * 通用连表分页查询（无排序）。
     *
     * <p>与 {@link #selectJoinPage(PageParam, Class, MPJLambdaWrapper)}
     * 的区别：接受更通用的 {@link MPJBaseJoin}，适配自定义的连表 wrapper。
     *
     * @param pageParam        分页参数
     * @param resultTypeClass  结果类型
     * @param joinQueryWrapper 连表查询条件
     * @return 分页结果
     */
    default <DTO> PageResult<DTO> selectJoinPage(PageParam pageParam, Class<DTO> resultTypeClass,
                                                 MPJBaseJoin<T> joinQueryWrapper) {
        Page<DTO> page = MyBatisPageUtils.toPage(pageParam);
        IPage<DTO> result = selectJoinPage(page, resultTypeClass, joinQueryWrapper);
        return MyBatisPageUtils.toPageResult(result);
    }

    // ==================== 单条记录查询 ====================

    /**
     * @deprecated 使用 String 作为字段名存在 SQL 注入风险（CWE-89）；
     * 请使用 {@link #selectOne(SFunction, Object)}
     */
    @Deprecated(since = "2026.08", forRemoval = true)
    default T selectOne(String field, Object value) {
        return selectOne(new QueryWrapper<T>().eq(field, value));
    }

    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * @deprecated 使用 String 作为字段名存在 SQL 注入风险；请使用 Lambda 版本
     */
    @Deprecated(since = "2026.08", forRemoval = true)
    default T selectOne(String field1, Object value1, String field2, Object value2) {
        return selectOne(new QueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default T selectOne(SFunction<T, ?> field1, Object value1,
                        SFunction<T, ?> field2, Object value2) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default T selectOne(SFunction<T, ?> field1, Object value1,
                        SFunction<T, ?> field2, Object value2,
                        SFunction<T, ?> field3, Object value3) {
        return selectOne(new LambdaQueryWrapper<T>()
                .eq(field1, value1).eq(field2, value2).eq(field3, value3));
    }

    // ==================== FOR UPDATE 查询 ====================

    /**
     * 获得满足条件的一条记录，并使用 {@code FOR UPDATE} 锁定。
     *
     * <p><b>必须在外层事务中调用</b>，否则锁会在语句结束时立即释放。
     *
     * <p><b>跨数据库兼容性：</b>本方法使用 {@code FOR UPDATE} 语法，
     * <b>仅支持 MySQL / PostgreSQL / Oracle / 达梦 / 金仓</b>。
     * SQL Server 不支持 {@code FOR UPDATE}，需在子接口中 override。
     *
     * @param queryWrapper 查询条件
     * @return 实体，无匹配返回 {@code null}
     */
    default T selectOneForUpdate(LambdaQueryWrapper<T> queryWrapper) {
        return selectOne(queryWrapper.last("FOR UPDATE"));
    }

    default T selectOneForUpdate(SFunction<T, ?> field, Object value) {
        return selectOneForUpdate(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default T selectOneForUpdate(SFunction<T, ?> field1, Object value1,
                                 SFunction<T, ?> field2, Object value2) {
        return selectOneForUpdate(new LambdaQueryWrapper<T>()
                .eq(field1, value1).eq(field2, value2));
    }

    default T selectOneForUpdate(SFunction<T, ?> field1, Object value1,
                                 SFunction<T, ?> field2, Object value2,
                                 SFunction<T, ?> field3, Object value3) {
        return selectOneForUpdate(new LambdaQueryWrapper<T>()
                .eq(field1, value1).eq(field2, value2).eq(field3, value3));
    }

    /**
     * 获得满足条件的首条记录，并使用 {@code FOR UPDATE} 锁定。
     *
     * <p><b>必须在外层事务中调用</b>。
     *
     * <p><b>跨数据库兼容性：</b>本方法使用 {@code LIMIT 1 FOR UPDATE} 语法。
     * <b>Oracle 与 SQL Server 不支持 {@code LIMIT}</b>，调用前需确认目标数据库类型。
     * 推荐改用 {@link #selectOneForUpdate} 配合业务层去重。
     *
     * @param queryWrapper 查询条件
     * @return 实体，无匹配返回 {@code null}
     */
    default T selectFirstForUpdate(LambdaQueryWrapper<T> queryWrapper) {
        return selectOne(queryWrapper.last("LIMIT 1 FOR UPDATE"));
    }

    // ==================== 首条 / 末条查询 ====================

    /**
     * 获取满足条件的首条记录。
     *
     * <p><b>语义说明：</b>“首条”由底层 SQL 的执行计划决定，
     * 若需确定性顺序，请在 wrapper 中显式 {@code orderByAsc(...)}。
     *
     * <p><b>实现说明：</b>用 {@code Page(1, 1)} + {@code setSearchCount(false)}
     * 实现 {@code LIMIT 1} 且不查总数——这是内部实现细节，
     * 不涉及参数校验。这里的 {@code Page} 构造是固定的 {@code (1, 1)}，
     * 与分页查询的“用户传入 pageNo / pageSize”是两回事。
     *
     * @param wrapper 查询条件
     * @return 首条记录；不存在返回 {@code null}
     */
    default T selectFirst(Wrapper<T> wrapper) {
        Page<T> page = new Page<>(1, FIRST_PAGE_SIZE);
        page.setSearchCount(false); // 不查询总数，提升性能
        selectPage(page, wrapper);
        List<T> records = page.getRecords();
        return records.isEmpty() ? null : records.get(0);
    }

    default T selectFirst(LambdaQueryWrapper<T> wrapper) {
        return selectFirst((Wrapper<T>) wrapper);
    }

    default T selectFirst(SFunction<T, ?> field, Object value) {
        return selectFirst(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default T selectFirst(SFunction<T, ?> field1, Object value1,
                          SFunction<T, ?> field2, Object value2) {
        return selectFirst(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    default T selectFirst(SFunction<T, ?> field1, Object value1,
                          SFunction<T, ?> field2, Object value2,
                          SFunction<T, ?> field3, Object value3) {
        return selectFirst(new LambdaQueryWrapper<T>()
                .eq(field1, value1).eq(field2, value2).eq(field3, value3));
    }

    /**
     * 获取满足条件的末条记录。
     *
     * <p><b>语义警告：</b>“末条”指查询结果列表的最后一项，
     * <b>不是数据库中“最新”的一条</b>。若需“最新一条”，
     * 请在 wrapper 中显式 {@code orderByDesc(创建时间)} 后再调用本方法。
     *
     * <p><b>性能警告：</b>本方法会把满足条件的全部记录加载到内存再取末条。
     * 结果集较大时应改用“显式排序 + {@link #selectFirst}”的方式。
     *
     * @param queryWrapper 查询条件（建议包含排序）
     * @return 末条记录；不存在返回 {@code null}
     */
    default T selectLast(LambdaQueryWrapper<T> queryWrapper) {
        return CollUtil.getLast(selectList(queryWrapper));
    }

    // ==================== 总数查询 ====================

    default Long selectCount() {
        return selectCount(new QueryWrapper<>());
    }

    /**
     * @deprecated 使用 String 作为字段名存在 SQL 注入风险；请使用 Lambda 版本
     */
    @Deprecated(since = "2026.08", forRemoval = true)
    default Long selectCount(String field, Object value) {
        return selectCount(new QueryWrapper<T>().eq(field, value));
    }

    default Long selectCount(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value));
    }

    // ==================== 列表查询 ====================

    default List<T> selectList() {
        return selectList(new QueryWrapper<>());
    }

    /**
     * @deprecated 使用 String 作为字段名存在 SQL 注入风险；请使用 Lambda 版本
     */
    @Deprecated(since = "2026.08", forRemoval = true)
    default List<T> selectList(String field, Object value) {
        return selectList(new QueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * @deprecated 使用 String 作为字段名存在 SQL 注入风险；请使用 Lambda 版本
     */
    @Deprecated(since = "2026.08", forRemoval = true)
    default List<T> selectList(String field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return Collections.emptyList();
        }
        return selectList(new QueryWrapper<T>().in(field, values));
    }

    /**
     * 按单个字段的集合值做 {@code IN} 查询。
     *
     * <p><b>为什么 {@code values} 为空时返回空列表：</b>
     * SQL 的 {@code IN ()} 是非法语法，{@code IN (NULL)} 永不匹配。
     * 与其让数据库报错或返回空结果，不如在应用层短路——
     * 这是“无查询条件时无操作返回”的转换语义，不是参数校验。
     * 调用方拿到空列表后仍可正常处理，不需要额外判空。
     */
    default List<T> selectList(SFunction<T, ?> field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapper<T>().in(field, values));
    }

    default List<T> selectList(SFunction<T, ?> field1, Object value1,
                               SFunction<T, ?> field2, Object value2) {
        return selectList(new LambdaQueryWrapper<T>()
                .eq(field1, value1).eq(field2, value2));
    }

    // ==================== 批量操作 ====================

    /**
     * 批量插入（使用默认批次大小 1000）。
     *
     * <p><b>注意：</b>若使用 SQL Server，批量插入后无法自动回填自增主键。
     * 如业务依赖主键回填，请在子接口中 override 本方法。
     *
     * <p>{@code entities} 为空时返回 {@code false}——这是“空集合无操作”的
     * 返回值约定，不是校验。调用方拿到 {@code false} 后正常继续即可。
     *
     * @param entities 实体集合
     * @return {@code true} 表示有实体被处理；{@code false} 表示入参为空
     */
    default boolean insertBatch(Collection<T> entities) {
        if (CollUtil.isEmpty(entities)) {
            return false;
        }
        return Db.saveBatch(entities);
    }

    /**
     * 批量插入（指定批次大小）。
     *
     * @param entities 实体集合
     * @param size     每批插入数量
     * @return {@code true} 表示有实体被处理；{@code false} 表示入参为空
     */
    default boolean insertBatch(Collection<T> entities, int size) {
        if (CollUtil.isEmpty(entities)) {
            return false;
        }
        return Db.saveBatch(entities, size);
    }

    /**
     * 批量更新（按主键）。
     *
     * @param entities 实体集合（必须包含主键）
     * @return {@code true} 表示有实体被处理；{@code false} 表示入参为空
     */
    default boolean updateBatch(Collection<T> entities) {
        if (CollUtil.isEmpty(entities)) {
            return false;
        }
        return Db.updateBatchById(entities);
    }

    /**
     * 批量更新（按主键，指定批次大小）。
     *
     * @see #updateBatch(Collection)
     */
    default boolean updateBatch(Collection<T> entities, int size) {
        if (CollUtil.isEmpty(entities)) {
            return false;
        }
        return Db.updateBatchById(entities, size);
    }

    /**
     * 全量更新（无条件）。
     *
     * @deprecated 无条件更新风险极高，请使用带条件的 {@code update(entity, wrapper)}
     */
    @Deprecated(since = "2026.08", forRemoval = true)
    default int updateBatch(T update) {
        return update(update, new QueryWrapper<>());
    }

    // ==================== 删除操作 ====================

    /**
     * @deprecated 使用 String 作为字段名存在 SQL 注入风险；
     * 请使用 {@link #delete(SFunction, Object)}
     */
    @Deprecated(since = "2026.08", forRemoval = true)
    default int delete(String field, Object value) {
        return delete(new QueryWrapper<T>().eq(field, value));
    }

    default int delete(SFunction<T, ?> field, Object value) {
        return delete(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default int deleteBatch(SFunction<T, ?> field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return 0;
        }
        return delete(new LambdaQueryWrapper<T>().in(field, values));
    }
}
