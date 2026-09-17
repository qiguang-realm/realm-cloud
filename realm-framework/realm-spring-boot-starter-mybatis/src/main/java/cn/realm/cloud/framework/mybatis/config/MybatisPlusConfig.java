package cn.realm.cloud.framework.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 的分页插件
 * <p>
 * 提示
 * <p>
 * 于 v3.5.9 起，PaginationInnerInterceptor 已分离出来。如需使用，则需单独引入 mybatis-plus-jsqlparser 依赖
 *
 * @author QI Guang
 */
@Configuration
// 通配符扫描："cn.realm.cloud.**.mapper"（扫描所有子包下的 mapper 包）
@MapperScan("cn.realm.cloud.**.mapper")
public class MybatisPlusConfig {

    /**
     * 添加分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); // 如果配置多个插件, 切记分页最后添加
        // 如果有多数据源可以不配具体类型, 否则都建议配上具体的 DbType
        return interceptor;
    }
}
