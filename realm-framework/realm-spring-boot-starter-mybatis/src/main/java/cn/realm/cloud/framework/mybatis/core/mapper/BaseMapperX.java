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
 * <p><b>安全说明（CWE-89 / OWASP ASVS 5.3.4）：</b>
 * 所有涉及字段名的方法，均提供 {@code String} 与 {@code SFunction} 两个版本。
 * {@code String} 版本因存在 SQL 注入风险，已标记 {@link Deprecated}；
 * 新代码必须使用 Lambda 版本。排序字段必须通过 {@code allowedFields} 白名单传入。
 *
 * <p><b>跨数据库兼容性：</b>
 * {@link #selectOneForUpdate} 与 {@link #selectFirstForUpdate} 依赖 {@code FOR UPDATE} 语法，
 * <b>仅支持 MySQL / PostgreSQL / Oracle / 达梦 / 金仓</b>；SQL Server 需在子接口中 override。
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
     */
    default PageResult<T> selectPage(PageParam pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, MyBatisPageUtils.EMPTY_SORTS, queryWrapper);
    }

    /**
     * 分页查询（带排序，白名单强校验）。
     *
     * @param pageParam     分页 + 排序参数
     * @param allowedFields 允许排序的字段白名单（数据库列名）
     * @param queryWrapper  查询条件
     */
    default PageResult<T> selectPage(SortablePageParam pageParam, Set<String> allowedFields,
                                     @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, pageParam.parseSortFields(allowedFields), queryWrapper);
    }

    /**
     * 分页查询（核心实现）。
     */
    default PageResult<T> selectPage(PageParam pageParam, List<SortField> sortFields,
                                     @Param("ew") Wrapper<T> queryWrapper) {
        // 不分页模式：查询全部并附加排序
        if (!pageParam.isPaged()) {
            MyBatisPageUtils.addOrder(queryWrapper, sortFields);
            List<T> list = selectList(queryWrapper);
            return PageResult.of(list, list.size(), list.size(), 1);
        }

        // 分页模式
        Page<T> page = MyBatisPageUtils.toPage(pageParam);
        MyBatisPageUtils.addOrder(page, sortFields);
        selectPage(page, queryWrapper);
        return MyBatisPageUtils.toPageResult(page);
    }

    // ==================== 连表分页查询 ====================

    /**
     * 连表分页查询（无排序）。
     */
    default <D> PageResult<D> selectJoinPage(PageParam pageParam, Class<D> clazz,
                                             MPJLambdaWrapper<T> lambdaWrapper) {
        if (!pageParam.isPaged()) {
            List<D> list = selectJoinList(clazz, lambdaWrapper);
            return PageResult.of(list, list.size(), list.size(), 1);
        }
        Page<D> page = MyBatisPageUtils.toPage(pageParam);
        IPage<D> result = selectJoinPage(page, clazz, lambdaWrapper);
        return MyBatisPageUtils.toPageResult(result);
    }

    /**
     * 连表分页查询（带排序，白名单强校验）。
     */
    default <D> PageResult<D> selectJoinPage(SortablePageParam pageParam, Set<String> allowedFields,
                                             Class<D> clazz, MPJLambdaWrapper<T> lambdaWrapper) {
        List<SortField> sortFields = pageParam.parseSortFields(allowedFields);
        if (!pageParam.isPaged()) {
            List<D> list = selectJoinList(clazz, lambdaWrapper);
            return PageResult.of(list, list.size(), list.size(), 1);
        }
        Page<D> page = MyBatisPageUtils.toPage(pageParam);
        MyBatisPageUtils.addOrder(page, sortFields);
        IPage<D> result = selectJoinPage(page, clazz, lambdaWrapper);
        return MyBatisPageUtils.toPageResult(result);
    }

    /**
     * 通用连表分页查询（无排序）。
     *
     * <p><b>修复：</b>原实现缺少 {@code !pageParam.isPaged()} 分支，
     * 当 {@code pageSize = -1} 时会让 MP 生成非法 SQL。
     */
    default <DTO> PageResult<DTO> selectJoinPage(PageParam pageParam, Class<DTO> resultTypeClass,
                                                 MPJBaseJoin<T> joinQueryWrapper) {
        if (!pageParam.isPaged()) {
            List<DTO> list = selectJoinList(resultTypeClass, joinQueryWrapper);
            return PageResult.of(list, list.size(), list.size(), 1);
        }
        Page<DTO> page = MyBatisPageUtils.toPage(pageParam);
        IPage<DTO> result = selectJoinPage(page, resultTypeClass, joinQueryWrapper);
        return MyBatisPageUtils.toPageResult(result);
    }

    // ==================== 单条记录查询 ====================

    /**
     * @deprecated 使用 String 作为字段名存在 SQL 注入风险（CWE-89）；请使用 {@link #selectOne(SFunction, Object)}
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
     * @deprecated 使用 String 作为字段名存在 SQL 注入风险；请使用 {@link #delete(SFunction, Object)}
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
