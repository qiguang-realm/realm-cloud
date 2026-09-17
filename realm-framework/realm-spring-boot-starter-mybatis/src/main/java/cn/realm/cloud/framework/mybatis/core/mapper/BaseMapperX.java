package cn.realm.cloud.framework.mybatis.core.mapper;

import cn.hutool.core.collection.CollUtil;
import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.framework.common.pojo.SortablePageParam;
import cn.realm.cloud.framework.common.pojo.SortingField;
import cn.realm.cloud.framework.mybatis.core.util.JdbcUtils;
import cn.realm.cloud.framework.mybatis.core.util.MyBatisUtils;
import com.baomidou.mybatisplus.annotation.DbType;
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

/**
 * 在 MyBatis Plus 的 BaseMapper 基础上拓展，提供更多的能力。
 * <p>
 * 该接口结合了：
 * <ul>
 *   <li>{@link BaseMapper} – 基础 CRUD 操作</li>
 *   <li>{@link MPJBaseMapper} – 连表查询支持</li>
 * </ul>
 *
 * <p><b>安全说明：</b>所有涉及排序字段的方法，均需确保 {@link SortingField # @link field} 已在业务层通过白名单校验，
 * 防止 SQL 注入。本接口不提供自动白名单过滤，请调用方自行保证。
 *
 * @param <T> 实体类型
 * @author QI Guang
 */
public interface BaseMapperX<T> extends MPJBaseMapper<T> {

    // ==================== 分页查询 ====================

    /**
     * 分页查询（支持排序）
     *
     * @param pageParam    可排序分页参数
     * @param queryWrapper 查询条件包装器
     * @return 分页结果，包含记录列表和总条数
     */
    default PageResult<T> selectPage(SortablePageParam pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, pageParam.getSortingFields(), queryWrapper);
    }

    /**
     * 分页查询（无排序）
     *
     * @param pageParam    分页参数
     * @param queryWrapper 查询条件包装器
     * @return 分页结果
     */
    default PageResult<T> selectPage(PageParam pageParam, @Param("ew") Wrapper<T> queryWrapper) {
        return selectPage(pageParam, null, queryWrapper);
    }

    /**
     * 分页查询（支持自定义排序字段集合）
     *
     * @param pageParam     分页参数
     * @param sortingFields 排序字段列表（可为空）
     * @param queryWrapper  查询条件包装器
     * @return 分页结果
     */
    default PageResult<T> selectPage(PageParam pageParam, Collection<SortingField> sortingFields,
                                     @Param("ew") Wrapper<T> queryWrapper) {
        // 特殊：不分页，直接查询全部（不分页模式）
        if (PageParam.PAGE_SIZE_NONE.equals(pageParam.getPageSize())) {
            MyBatisUtils.addOrder(queryWrapper, sortingFields);
            List<T> list = selectList(queryWrapper);
            return new PageResult<>(list, (long) list.size());
        }

        // 构建 MyBatis Plus 分页对象，并应用排序
        IPage<T> mpPage = MyBatisUtils.buildPage(pageParam, sortingFields);
        selectPage(mpPage, queryWrapper);
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal());
    }

    // ==================== 连表分页查询 ====================

    /**
     * 连表分页查询（无排序）
     *
     * @param pageParam     分页参数
     * @param clazz         返回结果类型
     * @param lambdaWrapper 连表查询条件包装器
     * @param <D>           返回结果泛型
     * @return 分页结果
     */
    default <D> PageResult<D> selectJoinPage(PageParam pageParam, Class<D> clazz, MPJLambdaWrapper<T> lambdaWrapper) {
        if (PageParam.PAGE_SIZE_NONE.equals(pageParam.getPageSize())) {
            List<D> list = selectJoinList(clazz, lambdaWrapper);
            return new PageResult<>(list, (long) list.size());
        }

        IPage<D> mpPage = MyBatisUtils.buildPage(pageParam);
        mpPage = selectJoinPage(mpPage, clazz, lambdaWrapper);
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal());
    }

    /**
     * 连表分页查询（支持排序）
     *
     * @param pageParam     可排序分页参数
     * @param clazz         返回结果类型
     * @param lambdaWrapper 连表查询条件包装器
     * @param <D>           返回结果泛型
     * @return 分页结果
     */
    default <D> PageResult<D> selectJoinPage(SortablePageParam pageParam, Class<D> clazz, MPJLambdaWrapper<T> lambdaWrapper) {
        if (PageParam.PAGE_SIZE_NONE.equals(pageParam.getPageSize())) {
            List<D> list = selectJoinList(clazz, lambdaWrapper);
            return new PageResult<>(list, (long) list.size());
        }

        IPage<D> mpPage = MyBatisUtils.buildPage(pageParam, pageParam.getSortingFields());
        mpPage = selectJoinPage(mpPage, clazz, lambdaWrapper);
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal());
    }

    /**
     * 通用连表分页查询（无排序）
     *
     * @param pageParam        分页参数
     * @param resultTypeClass  返回结果类型
     * @param joinQueryWrapper 连表查询包装器
     * @param <DTO>            返回结果泛型
     * @return 分页结果
     */
    default <DTO> PageResult<DTO> selectJoinPage(PageParam pageParam, Class<DTO> resultTypeClass, MPJBaseJoin<T> joinQueryWrapper) {
        IPage<DTO> mpPage = MyBatisUtils.buildPage(pageParam);
        selectJoinPage(mpPage, resultTypeClass, joinQueryWrapper);
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal());
    }

    // ==================== 单条记录查询（推荐使用 selectFirstOne） ====================

    /**
     * 根据条件查询单条记录（当有多条时抛出异常）
     *
     * @param field 字段名（字符串，存在 SQL 注入风险，请确保传入安全值）
     * @param value 字段值
     * @return 实体，若不存在返回 null
     * @deprecated 存在 SQL 注入风险，建议使用 {@link #selectOne(SFunction, Object)} 或 {@link #selectFirstOne(SFunction, Object)}
     */
    @Deprecated
    default T selectOne(String field, Object value) {
        return selectOne(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据条件查询单条记录（推荐，使用 Lambda 表达式，安全）
     *
     * @param field 字段 Lambda 引用
     * @param value 字段值
     * @return 实体，若不存在返回 null
     */
    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据多个条件查询单条记录（字符串字段名，存在 SQL 注入风险）
     *
     * @deprecated 建议使用 Lambda 版本
     */
    @Deprecated
    default T selectOne(String field1, Object value1, String field2, Object value2) {
        return selectOne(new QueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 根据多个条件查询单条记录（Lambda 安全）
     */
    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 根据三个条件查询单条记录（Lambda 安全）
     */
    default T selectOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2,
                        SFunction<T, ?> field3, Object value3) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2).eq(field3, value3));
    }

    // ==================== 获取第一条记录（性能优化版） ====================

    /**
     * 获取满足条件的第一条记录（使用分页限制，避免全表扫描）
     *
     * <p>此方法利用 MyBatis Plus 分页插件自动适配不同数据库的 LIMIT/TOP/ROWNUM，
     * 性能优于 {@code selectList} 全量查询后取第一条。
     *
     * @param wrapper 查询条件包装器
     * @return 第一条记录，若无则返回 null
     */
    default T selectFirstOne(LambdaQueryWrapper<T> wrapper) {
        Page<T> page = new Page<>(1, 1);
        page.setSearchCount(false); // 不查询总记录数，提升性能
        selectPage(page, wrapper);
        List<T> records = page.getRecords();
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * 根据单字段条件获取第一条记录
     */
    default T selectFirstOne(SFunction<T, ?> field, Object value) {
        return selectFirstOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据两个字段条件获取第一条记录
     */
    default T selectFirstOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectFirstOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    /**
     * 根据三个字段条件获取第一条记录
     */
    default T selectFirstOne(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2,
                             SFunction<T, ?> field3, Object value3) {
        return selectFirstOne(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2).eq(field3, value3));
    }

    // ==================== 总数查询 ====================

    /**
     * 查询总记录数（无条件）
     */
    default Long selectCount() {
        return selectCount(new QueryWrapper<>());
    }

    /**
     * 根据字段条件查询记录数（字符串字段名，存在 SQL 注入风险）
     *
     * @deprecated 建议使用 {@link #selectCount(SFunction, Object)} 版本
     */
    @Deprecated
    default Long selectCount(String field, Object value) {
        return selectCount(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据字段条件查询记录数（Lambda 安全）
     */
    default Long selectCount(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value));
    }

    // ==================== 列表查询（空集合优化） ====================

    /**
     * 查询全部记录
     */
    default List<T> selectList() {
        return selectList(new QueryWrapper<>());
    }

    /**
     * 根据字段条件查询列表（字符串字段名，存在 SQL 注入风险）
     *
     * @deprecated 建议使用 {@link #selectList(SFunction, Object)} 版本
     */
    @Deprecated
    default List<T> selectList(String field, Object value) {
        return selectList(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据字段条件查询列表（Lambda 安全）
     */
    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据字段 IN 条件查询列表（字符串字段名，存在 SQL 注入风险）
     *
     * @deprecated 建议使用 {@link #selectList(SFunction, Collection)} 版本
     */
    @Deprecated
    default List<T> selectList(String field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return Collections.emptyList(); // 避免无效查询，直接返回空列表
        }
        return selectList(new QueryWrapper<T>().in(field, values));
    }

    /**
     * 根据字段 IN 条件查询列表（Lambda 安全）
     */
    default List<T> selectList(SFunction<T, ?> field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapper<T>().in(field, values));
    }

    /**
     * 根据两个字段条件查询列表（Lambda 安全）
     */
    default List<T> selectList(SFunction<T, ?> field1, Object value1, SFunction<T, ?> field2, Object value2) {
        return selectList(new LambdaQueryWrapper<T>().eq(field1, value1).eq(field2, value2));
    }

    // ==================== 批量操作 ====================

    /**
     * 批量插入（适用于大量数据）
     *
     * <p><b>注意：</b> SQL Server 因 JDBC 驱动限制，批量插入后无法自动获取生成的主键，
     * 因此采用循环插入。如需高性能，可考虑使用 {@code SqlSession} 的 BATCH 执行器。
     *
     * @param entities 实体集合
     * @return true 表示至少有一条插入成功（存在数据），false 表示无数据或全部失败
     */
    default Boolean insertBatch(Collection<T> entities) {
        if (CollUtil.isEmpty(entities)) {
            return false;
        }
        DbType dbType = JdbcUtils.getDbType();
        if (JdbcUtils.isSQLServer(dbType)) {
            entities.forEach(this::insert);
            return true;
        }
        return Db.saveBatch(entities);
    }

    /**
     * 批量插入（指定批量大小）
     *
     * @param entities 实体集合
     * @param size     每批插入的数量（Db.saveBatch 默认为 1000）
     * @return true 表示至少有一条插入成功
     */
    default Boolean insertBatch(Collection<T> entities, int size) {
        if (CollUtil.isEmpty(entities)) {
            return false;
        }
        DbType dbType = JdbcUtils.getDbType();
        if (JdbcUtils.isSQLServer(dbType)) {
            entities.forEach(this::insert);
            return true;
        }
        return Db.saveBatch(entities, size);
    }

    /**
     * 批量更新（使用主键）
     *
     * @param entities 实体集合（必须包含主键）
     * @return true 表示至少有一条更新成功，false 表示无数据或全部失败
     */
    default Boolean updateBatch(Collection<T> entities) {
        return Db.updateBatchById(entities);
    }

    /**
     * 批量更新（指定批量大小）
     *
     * @param entities 实体集合
     * @param size     每批更新的数量
     * @return true 表示至少有一条更新成功
     */
    default Boolean updateBatch(Collection<T> entities, int size) {
        return Db.updateBatchById(entities, size);
    }

    /**
     * 全量更新（无条件，危险操作）
     *
     * @param update 更新的实体（非空字段作为更新内容）
     * @return 影响行数
     */
    default int updateBatch(T update) {
        return update(update, new QueryWrapper<>());
    }

    /**
     * 根据字段条件删除（字符串字段名，存在 SQL 注入风险）
     *
     * @deprecated 建议使用 {@link #delete(SFunction, Object)} 版本
     */
    @Deprecated
    default int delete(String field, String value) {
        return delete(new QueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据字段条件删除（Lambda 安全）
     *
     * @param field 字段 Lambda 引用
     * @param value 字段值
     * @return 删除的记录数
     */
    default int delete(SFunction<T, ?> field, Object value) {
        return delete(new LambdaQueryWrapper<T>().eq(field, value));
    }

    /**
     * 根据字段 IN 条件批量删除（Lambda 安全）
     *
     * @param field  字段 Lambda 引用
     * @param values 值集合
     * @return 删除的记录数
     */
    default int deleteBatch(SFunction<T, ?> field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return 0;
        }
        return delete(new LambdaQueryWrapper<T>().in(field, values));
    }
}
