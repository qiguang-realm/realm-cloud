package cn.realm.cloud.framework.security.config;

import cn.realm.cloud.framework.security.config.properties.RealmSecurityProperties;
import cn.realm.cloud.framework.security.config.properties.TokenProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * 安全组件自动配置入口。
 *
 * <p>依据：Spring Boot 自动配置规范 + ASVS L3 "安全控制集中化"。
 * 通过 AutoConfiguration.imports 注册，业务模块零代码获得安全能力。
 *
 * @author QI Guang
 */
@AutoConfiguration
@EnableConfigurationProperties({RealmSecurityProperties.class, TokenProperties.class})
@Import({
        SecurityFilterChainConfiguration.class,
        WebSecurityBeanConfiguration.class,
        OperateLogConfiguration.class,
        AuthEndpointsConfiguration.class
})
public class RealmSecurityAutoConfiguration {
}
