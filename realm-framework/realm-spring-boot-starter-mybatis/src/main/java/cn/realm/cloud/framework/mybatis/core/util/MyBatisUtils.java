package cn.realm.cloud.framework.mybatis.core.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.lang.func.LambdaUtil;
import cn.hutool.core.util.StrUtil;
import cn.realm.cloud.framework.common.enums.OrderDirection;
import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.SortingField;
import cn.realm.cloud.framework.mybatis.core.enums.DbTypeEnum;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * MyBatis 工具类
 *
 * <p>提供分页构建、排序处理、拦截器管理等通用功能。
 *
 * @author QI Guang
 */
public class MyBatisUtils {

    private static final String MYSQL_ESCAPE_CHARACTER = "`";

    /**
     * 构建 MyBatis Plus 分页对象（无排序）
     *
     * @param pageParam 分页参数
     * @param <T>       实体类型
     * @return 分页对象
     */
    public static <T> Page<T> buildPage(PageParam pageParam) {
        return buildPage(pageParam, null);
    }

    /**
     * 构建 MyBatis Plus 分页对象（支持排序）
     *
     * <p><b>安全说明：</b>调用方需确保 {@link SortingField#getField()} 已通过白名单校验，
     * 防止 SQL 注入。
     *
     * @param pageParam     分页参数
     * @param sortingFields 排序字段列表（可为空）
     * @param <T>           实体类型
     * @return 分页对象
     */
    public static <T> Page<T> buildPage(PageParam pageParam, Collection<SortingField> sortingFields) {
        // 页码 + 数量
        Page<T> page = new Page<>(pageParam.getPageNo(), pageParam.getPageSize());
        // 多表left join 的时候，要设置为 page.setOptimizeJoinOfCountSql(false); 才对，不然都会忽略调
        page.setOptimizeJoinOfCountSql(false);

        // 处理排序字段
        if (CollUtil.isNotEmpty(sortingFields)) {
            for (SortingField sortingField : sortingFields) {
                // 字段名转下划线（兼容数据库列名）
                String column = StrUtil.toUnderlineCase(sortingField.getField());
                // 判断排序方向
                boolean isAsc = sortingField.getOrder() == OrderDirection.ASC;
                page.addOrder(new OrderItem().setAsc(isAsc).setColumn(column));
            }
        }
        return page;
    }

    /**
     * 为查询包装器添加排序条件
     *
     * <p><b>安全说明：</b>调用方需确保 {@link SortingField#getField()} 已通过白名单校验。
     * 对于 {@link LambdaQueryWrapper}，该方法通过 {@code last} 拼接 ORDER BY 子句，
     * 可能覆盖已有的 ORDER BY 条件，使用时请注意。
     *
     * @param wrapper       查询包装器
     * @param sortingFields 排序字段列表（可为空）
     * @param <T>           实体类型
     * @throws IllegalArgumentException 不支持的包装器类型
     */
    @SuppressWarnings("PatternVariableCanBeUsed")
    public static <T> void addOrder(Wrapper<T> wrapper, Collection<SortingField> sortingFields) {
        if (CollUtil.isEmpty(sortingFields)) {
            return;
        }

        if (wrapper instanceof QueryWrapper<T>) {
            QueryWrapper<T> query = (QueryWrapper<T>) wrapper;
            for (SortingField sortingField : sortingFields) {
                String column = StrUtil.toUnderlineCase(sortingField.getField());
                boolean isAsc = sortingField.getOrder() == OrderDirection.ASC;
                query.orderBy(true, isAsc, column);
            }
        } else if (wrapper instanceof LambdaQueryWrapper<T>) {
            // LambdaQueryWrapper 不支持直接添加字符串字段排序，需通过 last 拼接
            LambdaQueryWrapper<T> lambdaQuery = (LambdaQueryWrapper<T>) wrapper;
            StringBuilder orderBy = new StringBuilder();
            for (SortingField sortingField : sortingFields) {
                if (orderBy.length() > 0) {
                    orderBy.append(", ");
                }
                String column = StrUtil.toUnderlineCase(sortingField.getField());
                String direction = sortingField.getOrder() == OrderDirection.ASC ? "ASC" : "DESC";
                orderBy.append(column).append(" ").append(direction);
            }
            lambdaQuery.last("ORDER BY " + orderBy);
        } else {
            throw new IllegalArgumentException("不支持的包装器类型: " + wrapper.getClass().getName());
        }
    }

    /**
     * 将拦截器添加到 MyBatis Plus 拦截器链的指定位置
     *
     * <p>由于 {@link MybatisPlusInterceptor} 不支持单独添加，需全量替换拦截器列表。
     *
     * @param interceptor MyBatis Plus 拦截器链
     * @param inner       待添加的拦截器
     * @param index       插入位置（0 为头部）
     */
    public static void addInterceptor(MybatisPlusInterceptor interceptor, InnerInterceptor inner, int index) {
        List<InnerInterceptor> inners = new ArrayList<>(interceptor.getInterceptors());
        inners.add(index, inner);
        interceptor.setInterceptors(inners);
    }

    /**
     * 获取 Table 对象的实际表名（去除 MySQL 转义字符）
     *
     * <p>MySQL 中表名可能被反引号包裹（如 `t_user`），此方法去除首尾的反引号。
     *
     * @param table 表对象
     * @return 去除转义字符后的表名
     */
    public static String getTableName(Table table) {
        String tableName = table.getName();
        if (tableName.startsWith(MYSQL_ESCAPE_CHARACTER) && tableName.endsWith(MYSQL_ESCAPE_CHARACTER)) {
            tableName = tableName.substring(1, tableName.length() - 1);
        }
        return tableName;
    }

    /**
     * 构建 JSQLParser 的 Column 对象
     *
     * @param tableName  表名
     * @param tableAlias 表别名（可为空）
     * @param column     字段名
     * @return Column 对象
     */
    public static Column buildColumn(String tableName, Alias tableAlias, String column) {
        if (tableAlias != null) {
            tableName = tableAlias.getName();
        }
        return new Column(tableName + StringPool.DOT + column);
    }

    /**
     * 生成跨数据库的 FIND_IN_SET 函数 SQL 片段
     *
     * <p>根据当前数据库类型，自动选择对应的函数实现（如 MySQL 的 FIND_IN_SET，
     * PostgreSQL 的 POSITION 等）。
     *
     * @param column 字段名
     * @param value  查询值（无需添加引号）
     * @return SQL 片段
     */
    public static String findInSet(String column, Object value) {
        DbType dbType = JdbcUtils.getDbType();
        return DbTypeEnum.getFindInSetTemplate(dbType)
                .replace("#{column}", column)
                .replace("#{value}", StrUtil.toString(value));
    }

    /**
     * 将驼峰命名字段转换为下划线命名
     *
     * @param func 字段 Lambda 表达式（如 User::getName）
     * @param <T>  实体类型
     * @return 下划线命名字段名
     */
    public static <T> String toUnderlineCase(Func1<T, ?> func) {
        String fieldName = LambdaUtil.getFieldName(func);
        return StrUtil.toUnderlineCase(fieldName);
    }
}
