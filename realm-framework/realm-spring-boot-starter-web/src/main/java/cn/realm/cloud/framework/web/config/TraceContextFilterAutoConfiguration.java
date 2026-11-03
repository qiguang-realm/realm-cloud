package cn.realm.cloud.framework.web.config;

import cn.realm.cloud.framework.web.core.filter.TraceContextFilter;
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
 * W3C Trace Context 过滤器自动配置。
 *
 * <p><b>职责：</b>在 Servlet Web 应用中注册 {@link TraceContextFilter}。
 *
 * <p><b>为什么不需要 {@code @ConditionalOnBean(OpenTelemetry.class)}？</b>
 * {@link TraceContextFilter} 通过 {@code GlobalOpenTelemetry.get()} 获取
 * OpenTelemetry 实例，而非依赖 Spring 容器中的 Bean。这样设计的优势：
 * <ul>
 *     <li>兼容 OTel Java Agent 模式：Agent 设置 {@code GlobalOpenTelemetry}，
 *         但不注册 Spring Bean；</li>
 *     <li>兼容 OTel SDK 模式：{@code AutoConfiguredOpenTelemetrySdk.initialize()}
 *         内部会设置 {@code GlobalOpenTelemetry}；</li>
 *     <li>兼容无追踪模式：{@code GlobalOpenTelemetry.get()} 返回 no-op 实例，
 *         不抛异常，Filter 正常注册但 trace_id 为空。</li>
 * </ul>
 *
 * <p><b>条件装配：</b>
 * <ul>
 *     <li>{@link ConditionalOnWebApplication}：仅 Servlet Web 应用生效；</li>
 *     <li>{@link ConditionalOnProperty}：通过 {@code realm.web.trace-context.enabled=false}
 *         可禁用；</li>
 *     <li>{@link ConditionalOnMissingBean}：业务方若已定义 {@link TraceContextFilter}，
 *         则不注册，允许覆盖。</li>
 * </ul>
 *
 * <p><b>Order 选择：</b>{@link Ordered#HIGHEST_PRECEDENCE}——
 * Trace Context 必须最早建立，让后续所有 Filter（含 Spring Security）的日志都带上 trace_id。
 *
 * @author QI Guang
 * @see TraceContextFilter
 * @see TraceContextProperties
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TraceContextProperties.class)
public class TraceContextFilterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TraceContextFilterAutoConfiguration.class);

    /**
     * 注册 {@link TraceContextFilter}。
     *
     * <p><b>为什么用 {@link FilterRegistrationBean}？</b>
     * <ul>
     *     <li>精确设置 Order（保证 Trace Context 最早建立）</li>
     *     <li>设置 Filter 名称（便于运维排查）</li>
     *     <li>设置 URL 匹配规则</li>
     * </ul>
     */
    @Bean
    @ConditionalOnProperty(prefix = "realm.web.trace-context", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(TraceContextFilter.class)
    public FilterRegistrationBean<TraceContextFilter> traceContextFilter(
            TraceContextProperties properties) {

        FilterRegistrationBean<TraceContextFilter> registration =
                new FilterRegistrationBean<>(new TraceContextFilter(properties));
        registration.setName("traceContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");

        log.info("[traceContextFilter] 注册 W3C Trace Context 过滤器，order=HIGHEST_PRECEDENCE, writeResponseHeader={}",
                properties.isWriteResponseHeader());
        return registration;
    }
}
