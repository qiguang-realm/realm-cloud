package cn.realm.cloud.framework.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置。
 *
 * <p>注册 {@link MybatisPlusInterceptor}，并挂载分页内部拦截器
 * {@link PaginationInnerInterceptor}。
 *
 * <p>注意：自 MyBatis-Plus v3.5.9 起，{@code PaginationInnerInterceptor} 已被拆分，
 * 启用分页时需单独引入 {@code mybatis-plus-jsqlparser} 依赖。
 *
 * @author QI Guang
 * @see <a href="https://baomidou.com/plugins/">MyBatis-Plus 插件文档</a>
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfig {

    /**
     * 构建 MyBatis-Plus 拦截器链。
     *
     * <p>当前仅挂载分页拦截器；若后续扩展多个插件，分页拦截器必须置于链尾。
     *
     * @return 已配置的 {@link MybatisPlusInterceptor}
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页拦截器；如配置多个插件，必须最后添加
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 若使用多数据源，可省略具体 DbType；否则建议显式指定
        return interceptor;
    }
}
