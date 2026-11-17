package cn.realm.cloud.framework.mybatisplus.autoconfigure;

import cn.realm.cloud.framework.mybatisplus.properties.RealmMybatisPlusProperties;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 全局配置：注册分页拦截器。
 *
 * <p><b>只在该 Bean 不存在时生效</b>：如果应用或其它 starter 已经定义了
 * {@link MybatisPlusInterceptor}，本配置整体退让，避免覆盖 MyBatis-Plus
 * 自动配置对 {@code InnerInterceptorSupplier} 的收集机制。
 *
 * <p><b>{@link DbType} 从配置读取</b>：默认为空，由 MyBatis-Plus 自动检测；
 * 多数据源或多方言场景下，应用通过 {@code realm.mybatis-plus.db-type} 显式指定。
 *
 * <p><b>扩展约束：</b>如果未来需要追加 Realm 的内置拦截器（乐观锁、防全表攻击），
 * 应优先实现 {@code InnerInterceptorSupplier} 并通过 {@code @Order} 声明顺序，
 * 而不是在本类中硬编码 {@code addInnerInterceptor}。直接修改本类的链会使
 * {@code realm-biz-data-scope}、{@code realm-multitenancy} 等下游模块的
 * 拦截器顺序失去可控性。
 *
 * @author QI Guang
 * @see <a href="https://baomidou.com/plugins/">MyBatis-Plus 插件文档</a>
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnSingleCandidate(DataSource.class)
@ConditionalOnProperty(prefix = "realm.mybatis-plus", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(RealmMybatisPlusProperties.class)
public class RealmMybatisPlusAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(RealmMybatisPlusProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页拦截器：位于 SQL 改写类（租户/数据范围）之后、防护类之前
        // 多方言场景下必须显式指定 DbType；未指定时依赖 MyBatis-Plus 自动检测（仅单一方言可靠）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        if (properties.getDbType() != null) {
            pagination.setDbType(properties.getDbType());
        }
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
