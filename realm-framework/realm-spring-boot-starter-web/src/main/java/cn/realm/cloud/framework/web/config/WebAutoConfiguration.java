package cn.realm.cloud.framework.web.config;

import cn.realm.cloud.framework.web.core.filter.TraceIdFilter;
import cn.realm.cloud.framework.web.core.handler.GlobalExceptionHandler;
import cn.realm.cloud.framework.web.core.handler.GlobalResponseBodyAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Web 层自动配置。
 *
 * <p><b>职责：</b>将 Web 层的核心组件注册到 Spring 容器。
 *
 * <p><b>注册的组件：</b>
 * <ul>
 *     <li>{@link TraceIdFilter}：traceId 生成与传递（Order = HIGHEST_PRECEDENCE）</li>
 *     <li>{@link GlobalExceptionHandler}：全局异常处理</li>
 *     <li>{@link GlobalResponseBodyAdvice}：响应体自动包装</li>
 * </ul>
 *
 * <p><b>为什么不用 {@code @ComponentScan}？</b>
 * <ul>
 *     <li>{@code realm-spring-boot-starter-web} 是<b>框架模块</b>，
 *         不应被业务模块的 {@code @ComponentScan} 扫描到</li>
 *     <li>使用 Spring Boot 3 的 {@code @AutoConfiguration} + {@code AutoConfiguration.imports}，
 *         实现"开箱即用"（引入依赖即生效）</li>
 * </ul>
 *
 * <p><b>条件装配：</b>
 * <ul>
 *     <li>{@link ConditionalOnWebApplication}：仅 Servlet Web 应用生效</li>
 *     <li>{@link ConditionalOnMissingBean}：业务方若已定义同类型 Bean，则不注册，
 *         允许业务方覆盖默认实现</li>
 *     <li>{@link ConditionalOnProperty}：通过配置开关禁用（如 {@code realm.web.trace-id.enabled=false}）</li>
 * </ul>
 *
 * @author QI Guang
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TraceIdProperties.class)
public class WebAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebAutoConfiguration.class);

    /**
     * 注册 {@link GlobalExceptionHandler}。
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        log.info("[globalExceptionHandler] 注册全局异常处理器");
        return new GlobalExceptionHandler();
    }

    /**
     * 注册 {@link GlobalResponseBodyAdvice}。
     */
    @Bean
    @ConditionalOnMissingBean(GlobalResponseBodyAdvice.class)
    public GlobalResponseBodyAdvice globalResponseBodyAdvice() {
        log.info("[globalResponseBodyAdvice] 注册全局响应体包装器");
        return new GlobalResponseBodyAdvice();
    }

    /**
     * 注册 {@link TraceIdFilter}。
     *
     * <p><b>为什么用 {@link FilterRegistrationBean}？</b>
     * 直接声明为 {@code @Bean} 时，Filter 的 Order 由 Spring Boot 自动推断，
     * 无法精确控制。用 {@code FilterRegistrationBean} 可以：
     * <ul>
     *     <li>精确设置 Order（保证 traceId 最早生成）</li>
     *     <li>设置 URL 匹配规则（如排除静态资源）</li>
     *     <li>设置 Filter 名称（便于运维排查）</li>
     * </ul>
     *
     * <p><b>Order 选择：</b>{@link Ordered#HIGHEST_PRECEDENCE}——traceId 必须最早生成，
     * 让后续所有 Filter（含 Spring Security）的日志都带上 traceId。
     * 与 {@code GlobalExceptionHandler}（+100）、{@code GlobalResponseBodyAdvice}（+200）
     * 形成严格顺序。
     */
    @Bean
    @ConditionalOnProperty(prefix = "realm.web.trace-id", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(TraceIdFilter.class)
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter(TraceIdProperties properties) {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter(properties));
        registration.setName("traceIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        // 拦截所有路径（Filter 内部的 shouldNotFilter 处理静态资源排除）
        registration.addUrlPatterns("/*");
        log.info("[traceIdFilter] 注册 TraceId 过滤器，header={}, order=HIGHEST_PRECEDENCE",
                properties.getHeaderName());
        return registration;
    }
}
