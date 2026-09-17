package cn.realm.cloud.framework.mybatis.core.util;

import cn.realm.cloud.framework.common.util.spring.SpringUtils;
import cn.realm.cloud.framework.mybatis.core.enums.DbTypeEnum;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC 工具类
 *
 * @author QI Guang
 */
public class JdbcUtils {

    /**
     * 判断数据库连接是否正常
     *
     * @param url      数据库 URL
     * @param username 用户名
     * @param password 密码
     * @return true: 连接正常; false: 连接失败
     */
    public static boolean isConnectionOK(String url, String username, String password) {
        // 注：连接超时由驱动默认值决定，若需自定义超时，请使用 DriverManager.getConnection(url, info) 并设置 connectTimeout 属性
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 根据 JDBC URL 获取数据库类型
     *
     * @param url JDBC URL
     * @return 数据库类型
     */
    public static DbType getDbType(String url) {
        return com.baomidou.mybatisplus.extension.toolkit.JdbcUtils.getDbType(url);
    }

    /**
     * 获取当前数据源对应的数据库类型
     *
     * @return 数据库类型
     * @throws IllegalStateException 当无法获取数据源或连接失败时抛出
     */
    public static DbType getDbType() {
        DataSource dataSource;
        try {
            // 优先获取动态数据源
            DynamicRoutingDataSource dynamicRoutingDataSource = SpringUtils.getBean(DynamicRoutingDataSource.class);
            dataSource = dynamicRoutingDataSource.determineDataSource();
        } catch (NoSuchBeanDefinitionException e) {
            // 降级为普通数据源
            dataSource = SpringUtils.getBean(DataSource.class);
        }

        try (Connection conn = dataSource.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName();
            return DbTypeEnum.find(productName);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get database type from connection", e);
        }
    }

    /**
     * 判断 JDBC 连接是否为 SQL Server 数据库
     *
     * @param url JDBC URL
     * @return true: SQL Server; false: 其他
     */
    public static boolean isSQLServer(String url) {
        DbType dbType = getDbType(url);
        return isSQLServer(dbType);
    }

    /**
     * 判断数据库类型是否为 SQL Server
     *
     * @param dbType 数据库类型
     * @return true: SQL Server; false: 其他
     */
    public static boolean isSQLServer(DbType dbType) {
        return dbType == DbType.SQL_SERVER || dbType == DbType.SQL_SERVER2005;
    }
}
