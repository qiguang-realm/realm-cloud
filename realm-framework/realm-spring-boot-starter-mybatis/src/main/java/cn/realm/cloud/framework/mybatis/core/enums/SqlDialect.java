package cn.realm.cloud.framework.mybatis.core.enums;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SQL 方言枚举，对 MyBatis-Plus 的 {@link DbType} 增强，补充数据库产品名与 SQL 模板信息。
 *
 * <p><b>能力：</b>
 * <ul>
 *     <li>通过 JDBC 元数据中的产品名反查 {@link DbType}</li>
 *     <li>提供各数据库的“逗号分隔字符串包含判断”（{@code FIND_IN_SET}）SQL 模板</li>
 * </ul>
 *
 * <p><b>命名说明：</b>本枚举命名为 {@code SqlDialect}（SQL 方言），
 * 而非 {@code DbTypeEnum}。SQL 方言是数据库领域的标准术语，
 * 精准表达了“不同数据库 SQL 语法差异”这一核心职责，
 * 且避免 Java 语言规范 §8.9 与 Google Java Style §5.2.2 中
 * “枚举类型名不应包含冗余 {@code Enum} 后缀”的规范问题。
 *
 * <p><b>扩展指引：</b>新增数据库时，在枚举末尾追加常量，并确保
 * {@code productName} 与 JDBC {@code DatabaseMetaData#getDatabaseProductName()}
 * 的返回值<b>完全一致</b>（区分大小写、空格、版本号后缀）。
 *
 * @author QI Guang
 */
@Getter
@AllArgsConstructor
public enum SqlDialect {

    // ==================== 数据库定义 ====================

    /**
     * H2（嵌入式测试数据库）。
     */
    H2(DbType.H2, "H2",
            "POSITION(',' || CAST(#{value} AS VARCHAR) || ',' IN ',' || #{column} || ',') > 0"),

    /**
     * MySQL。
     */
    MYSQL(DbType.MYSQL, "MySQL",
            "FIND_IN_SET(#{value}, #{column}) <> 0"),

    /**
     * Oracle。
     */
    ORACLE(DbType.ORACLE, "Oracle",
            "INSTR(',' || #{column} || ',', ',' || #{value} || ',') > 0"),

    /**
     * PostgreSQL。
     *
     * <p>华为 openGauss 使用与 PostgreSQL 相同的 {@code ProductName}，
     * 因此共用同一枚举值。
     */
    POSTGRESQL(DbType.POSTGRE_SQL, "PostgreSQL",
            "POSITION(',' || CAST(#{value} AS VARCHAR) || ',' IN ',' || #{column} || ',') > 0"),

    /**
     * Microsoft SQL Server（2012 及以上）。
     */
    SQL_SERVER(DbType.SQL_SERVER, "Microsoft SQL Server",
            "CHARINDEX(',' + CAST(#{value} AS varchar(255)) + ',', ',' + #{column} + ',') > 0"),

    /**
     * Microsoft SQL Server 2005。
     */
    SQL_SERVER_2005(DbType.SQL_SERVER2005, "Microsoft SQL Server 2005",
            "CHARINDEX(',' + CAST(#{value} AS varchar(255)) + ',', ',' + #{column} + ',') > 0"),

    /**
     * 达梦 DM8。
     */
    DM(DbType.DM, "DM DBMS",
            "FIND_IN_SET(#{value}, #{column}) <> 0"),

    /**
     * 人大金仓 KingbaseES。
     */
    KINGBASE_ES(DbType.KINGBASE_ES, "KingbaseES",
            "POSITION(',' || CAST(#{value} AS VARCHAR) || ',' IN ',' || #{column} || ',') > 0"),

    /**
     * OceanBase。
     */
    OCEAN_BASE(DbType.OCEAN_BASE, "OceanBase",
            "FIND_IN_SET(#{value}, #{column}) <> 0"),
    ;

    // ==================== 索引 ====================

    /**
     * 按数据库产品名索引（不可变）。
     * <p>键来源于 JDBC {@code DatabaseMetaData#getDatabaseProductName()}。
     */
    private static final Map<String, SqlDialect> BY_PRODUCT_NAME =
            Collections.unmodifiableMap(Arrays.stream(values())
                    .collect(Collectors.toMap(SqlDialect::getProductName, Function.identity())));

    /**
     * 按 MyBatis-Plus 的 {@link DbType} 索引（不可变）。
     */
    private static final Map<DbType, SqlDialect> BY_MP_DB_TYPE =
            Collections.unmodifiableMap(Arrays.stream(values())
                    .collect(Collectors.toMap(SqlDialect::getMpDbType, Function.identity())));

    // ==================== 字段 ====================

    /**
     * MyBatis-Plus 的数据库类型。
     */
    private final DbType mpDbType;

    /**
     * 数据库产品名（与 JDBC 元数据返回值一致）。
     */
    private final String productName;

    /**
     * “逗号分隔字符串包含判断”的 SQL 模板。
     * <p>模板中占位符 {@code #{value}} 与 {@code #{column}} 由调用方填充。
     */
    private final String findInSetTemplate;

    // ==================== 静态方法 ====================

    /**
     * 根据 JDBC 产品名反查 {@link DbType}。
     *
     * <p>产品名来源：{@code Connection#getMetaData()#getDatabaseProductName()}。
     * 若产品名未匹配任何枚举值，返回 {@link DbType#OTHER} 而非 {@code null}，
     * 避免下游出现空指针（CWE-476）。
     *
     * @param databaseProductName JDBC 产品名，可为空白
     * @return 匹配的 {@link DbType}；未知产品返回 {@link DbType#OTHER}
     */
    public static DbType find(String databaseProductName) {
        if (StrUtil.isBlank(databaseProductName)) {
            return DbType.OTHER;
        }
        SqlDialect matched = BY_PRODUCT_NAME.get(databaseProductName);
        return matched != null ? matched.getMpDbType() : DbType.OTHER;
    }

    /**
     * 根据 JDBC 产品名反查 {@link SqlDialect} 枚举。
     *
     * <p>与 {@link #find(String)} 的区别：本方法返回枚举本身，便于访问
     * {@link #getFindInSetTemplate()} 等扩展属性；未知产品返回 {@link Optional#empty()}。
     *
     * @param databaseProductName JDBC 产品名，可为空白
     * @return 匹配的枚举值；未匹配返回 {@link Optional#empty()}
     */
    public static Optional<SqlDialect> findDialect(String databaseProductName) {
        if (StrUtil.isBlank(databaseProductName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_PRODUCT_NAME.get(databaseProductName));
    }

    /**
     * 根据 MyBatis-Plus 的 {@link DbType} 反查 {@link SqlDialect} 枚举。
     *
     * @param dbType MyBatis-Plus 的数据库类型
     * @return 匹配的枚举值；未匹配返回 {@link Optional#empty()}
     */
    public static Optional<SqlDialect> findDialect(DbType dbType) {
        if (dbType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_MP_DB_TYPE.get(dbType));
    }

    /**
     * 获取指定数据库的“逗号分隔字符串包含判断”SQL 模板。
     *
     * @param dbType MyBatis-Plus 的数据库类型
     * @return SQL 模板
     * @throws IllegalArgumentException 当 {@code dbType} 不支持 FIND_IN_SET 时抛出
     */
    public static String getFindInSetTemplate(DbType dbType) {
        return findDialect(dbType)
                .map(SqlDialect::getFindInSetTemplate)
                .filter(StrUtil::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException(
                        "FIND_IN_SET is not supported for dbType: " + dbType));
    }

    // ==================== 实例方法 ====================

    /**
     * 是否为 SQL Server 系列（含 2005）。
     *
     * @return {@code true} 表示 SQL Server 或 SQL Server 2005
     */
    public boolean isSqlServer() {
        return this == SQL_SERVER || this == SQL_SERVER_2005;
    }
}
