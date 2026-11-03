package cn.realm.cloud.framework.security.config;

import cn.realm.cloud.framework.security.auth.AuthController;
import cn.realm.cloud.framework.security.auth.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证端点 Bean 注册。
 *
 * <p>AuthController 位于 starter 包内，可能不在业务应用的 @ComponentScan 范围内，
 * 通过显式 @Bean 注册，避免依赖组件扫描。
 *
 * @author QI Guang
 */
@Configuration(proxyBeanMethods = false)
public class AuthEndpointsConfiguration {

    @Bean
    public AuthController authController(AuthService authService) {
        return new AuthController(authService);
    }
}
