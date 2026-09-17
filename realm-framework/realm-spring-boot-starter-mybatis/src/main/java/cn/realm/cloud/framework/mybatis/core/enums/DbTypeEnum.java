package cn.realm.cloud.framework.mybatis.core.enums;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 针对 MyBatis Plus 的 {@link DbType} 增强，补充更多信息
 *
 * @author QI Guang
 */
@Getter
@AllArgsConstructor
public enum DbTypeEnum {

    /**
     * H2
     * <p>
     * 注意：H2 不支持 find_in_set 函数
     */
    H2(DbType.H2, "H2", ""),

    /**
     * MySQL
     */
    MY_SQL(DbType.MYSQL, "MySQL", "FIND_IN_SET('#{value}', #{column}) <> 0"),

    /**
     * Oracle
     */
    ORACLE(DbType.ORACLE, "Oracle", "FIND_IN_SET('#{value}', #{column}) <> 0"),

    /**
     * PostgreSQL
     * <p>
     * 华为 openGauss 使用 ProductName 与 PostgreSQL 相同
     */
    POSTGRE_SQL(DbType.POSTGRE_SQL, "PostgreSQL", "POSITION('#{value}' IN #{column}) <> 0"),

    /**
     * SQL Server
     */
    SQL_SERVER(DbType.SQL_SERVER, "Microsoft SQL Server", "CHARINDEX(',' + #{value} + ',', ',' + #{column} + ',') <> 0"),

    /**
     * SQL Server 2005
     */
    SQL_SERVER2005(DbType.SQL_SERVER2005, "Microsoft SQL Server 2005", "CHARINDEX(',' + #{value} + ',', ',' + #{column} + ',') <> 0"),

    /**
     * 达梦
     */
    DM(DbType.DM, "DM DBMS", "FIND_IN_SET('#{value}', #{column}) <> 0"),

    /**
     * 人大金仓
     */
    KINGBASE_ES(DbType.KINGBASE_ES, "KingbaseES", "POSITION('#{value}' IN #{column}) <> 0"),

    /**
     * OceanBase
     */
    OCEAN_BASE(DbType.OCEAN_BASE, "OceanBase", "FIND_IN_SET('#{value}', #{column}) <> 0");

    // 私有不可变映射，避免外部直接访问或修改
    private static final Map<String, DbTypeEnum> MAP_BY_NAME;

    private static final Map<DbType, DbTypeEnum> MAP_BY_MP;

    static {
        Map<String, DbTypeEnum> nameMap = Arrays.stream(values())
                .collect(Collectors.toMap(DbTypeEnum::getProductName, Function.identity()));
        Map<DbType, DbTypeEnum> mpMap = Arrays.stream(values())
                .collect(Collectors.toMap(DbTypeEnum::getMpDbType, Function.identity()));
        MAP_BY_NAME = Collections.unmodifiableMap(nameMap);
        MAP_BY_MP = Collections.unmodifiableMap(mpMap);
    }

    /**
     * MyBatis Plus 类型
     */
    private final DbType mpDbType;

    /**
     * 数据库产品名
     */
    private final String productName;

    /**
     * SQL FIND_IN_SET 模板
     */
    private final String findInSetTemplate;

    /**
     * 根据数据库产品名获取对应的 MyBatis Plus DbType
     *
     * @param databaseProductName 数据库产品名（大小写敏感，建议使用原始返回）
     * @return 对应的 DbType，若未找到则返回 null
     */
    @Nullable
    public static DbType find(@Nullable String databaseProductName) {
        if (StrUtil.isBlank(databaseProductName)) {
            return null;
        }
        // 去除前后空格，增强容错性
        String trimmed = databaseProductName.trim();
        DbTypeEnum dbTypeEnum = MAP_BY_NAME.get(trimmed);
        return dbTypeEnum != null ? dbTypeEnum.getMpDbType() : null;
    }

    /**
     * 根据 MyBatis Plus DbType 获取 FIND_IN_SET 模板
     *
     * @param dbType MyBatis Plus 数据库类型
     * @return FIND_IN_SET 模板字符串
     * @throws IllegalArgumentException      如果 dbType 为 null 或不支持
     * @throws UnsupportedOperationException 如果对应数据库不支持 FIND_IN_SET（如 H2）
     */
    public static String getFindInSetTemplate(@Nullable DbType dbType) {
        if (dbType == null) {
            throw new IllegalArgumentException("DbType 不能为 null");
        }
        DbTypeEnum dbTypeEnum = MAP_BY_MP.get(dbType);
        if (dbTypeEnum == null) {
            throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
        String template = dbTypeEnum.getFindInSetTemplate();
        if (StrUtil.isBlank(template)) {
            throw new UnsupportedOperationException(
                    String.format("数据库 %s (%s) 不支持 FIND_IN_SET 函数",
                            dbTypeEnum.getProductName(), dbType));
        }
        return template;
    }

    /**
     * 根据数据库产品名直接获取 FIND_IN_SET 模板（便捷方法）
     *
     * @param databaseProductName 数据库产品名（大小写敏感）
     * @return FIND_IN_SET 模板字符串
     * @throws IllegalArgumentException 如果产品名未知或对应数据库不支持
     */
    public static String getFindInSetTemplateByProductName(@Nullable String databaseProductName) {
        if (StrUtil.isBlank(databaseProductName)) {
            throw new IllegalArgumentException("数据库产品名不能为空");
        }
        String trimmed = databaseProductName.trim();
        DbTypeEnum dbTypeEnum = MAP_BY_NAME.get(trimmed);
        if (dbTypeEnum == null) {
            throw new IllegalArgumentException("未知的数据库产品名: " + trimmed);
        }
        String template = dbTypeEnum.getFindInSetTemplate();
        if (StrUtil.isBlank(template)) {
            throw new UnsupportedOperationException(
                    String.format("数据库 %s (%s) 不支持 FIND_IN_SET 函数",
                            dbTypeEnum.getProductName(), dbTypeEnum.getMpDbType()));
        }
        return template;
    }
}
