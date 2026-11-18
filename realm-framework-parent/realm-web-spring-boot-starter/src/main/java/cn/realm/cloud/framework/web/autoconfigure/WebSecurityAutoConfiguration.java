package cn.realm.cloud.framework.web.autoconfigure;

import cn.realm.cloud.framework.web.properties.WebSecurityProperties;
import cn.realm.cloud.framework.web.security.ClientIpResolver;
import cn.realm.cloud.framework.web.security.SecurityHeadersFilter;
import cn.realm.cloud.framework.web.security.WebCorsConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Set;

/**
 * Web 安全基线自动配置。
 *
 * <p><b>职责：</b>提供符合 OWASP 推荐的安全默认值。
 * <ul>
 *     <li>注入安全响应头（X-Content-Type-Options, X-Frame-Options 等）</li>
 *     <li>配置全局 CORS 策略（默认拒绝跨域）</li>
 * </ul>
 *
 * <p><b>为什么不依赖 Spring Security：</b>
 * 响应头注入和 CORS 属于传输层的安全基线，不涉及复杂的认证授权逻辑。
 * 使用原生 Servlet Filter 和 Spring MVC 机制可以保持 starter 的轻量化，
 * 避免因引入 Spring Security 导致下游应用的安全配置被意外接管。
 *
 * @author QI Guang
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "realm.web.security", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WebSecurityProperties.class)
public class WebSecurityAutoConfiguration {

    /**
     * 安全响应头过滤器。
     *
     * <p><b>执行顺序：</b>设置为 {@code HIGHEST_PRECEDENCE + 5}，
     * 位于 TraceContextFilter (+10) 和 LoggingFilter (+20) 之前。
     * 确保即使发生异常进入错误处理器，响应中依然包含安全头。
     */
    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.HIGHEST_PRECEDENCE + 5)
    public SecurityHeadersFilter securityHeadersFilter(WebSecurityProperties properties) {
        return new SecurityHeadersFilter(properties);
    }

    /**
     * 全局 CORS 配置器。
     *
     * <p>实现 {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer}，
     * 默认不开放任何跨域来源。如果下游应用配置了白名单，则按白名单放行，
     * 并自动将 W3C Trace Context 头暴露给前端。
     */
    @Bean
    @ConditionalOnMissingBean
    public WebCorsConfigurer webCorsConfigurer(WebSecurityProperties properties) {
        return new WebCorsConfigurer(properties);
    }

    /**
     * 客户端IP安全解析器。
     * <p>遵循 OWASP ASVS 防IP伪造规则：仅当直连地址属于可信代理时，才解析转发头。
     *
     * @param properties web安全配置，读取 trustedProxies 可信代理列表
     * @return ClientIpResolver 单例Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientIpResolver clientIpResolver(WebSecurityProperties properties) {
        Set<String> trustedProxies = properties.getTrustedProxies();
        return new ClientIpResolver(trustedProxies);
    }
}
