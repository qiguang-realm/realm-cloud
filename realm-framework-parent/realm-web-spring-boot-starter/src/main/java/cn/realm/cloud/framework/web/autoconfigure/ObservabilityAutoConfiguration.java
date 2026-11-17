package cn.realm.cloud.framework.web.autoconfigure;

import cn.realm.cloud.framework.web.logging.RequestLoggingFilter;
import cn.realm.cloud.framework.web.logging.SensitiveDataMasker;
import cn.realm.cloud.framework.web.logging.masker.BodyFieldMasker;
import cn.realm.cloud.framework.web.logging.masker.DataMasker;
import cn.realm.cloud.framework.web.logging.masker.HeaderMasker;
import cn.realm.cloud.framework.web.logging.masker.QueryParamMasker;
import cn.realm.cloud.framework.web.properties.ObservabilityProperties;
import cn.realm.cloud.framework.web.trace.TraceContextFilter;
import cn.realm.cloud.framework.web.trace.TraceContextInjector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * 可观测性（追踪 + 日志）自动配置。
 *
 * <p><b>职责：</b>装配 W3C Trace Context 的全链路追踪，以及符合 OWASP ASVS V16 的结构化日志脱敏。
 * <ul>
 *     <li><b>Tracing：</b>入站提取 {@code traceparent}，出站注入上下文（供 Feign/MQ 复用）。</li>
 *     <li><b>Logging：</b>请求日志记录，以及按载体（Body/Query/Header）的敏感数据脱敏。</li>
 * </ul>
 *
 * <p><b>装配顺序：</b>
 * <ul>
 *     <li>本配置在 {@code WebSecurityAutoConfiguration} 之后，确保安全头已就绪。</li>
 *     <li>内部的 Filter 顺序由 {@code @Order} 严格控制：追踪(+10) 优先于日志(+20)，
 *         确保日志能正确携带 {@code traceId}。</li>
 * </ul>
 *
 * @author QI Guang
 */
@AutoConfiguration(after = WebSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "realm.web.observability", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    // ==================== Tracing ====================

    /**
     * 出站上下文注入器。
     *
     * <p>供下游模块（如 OpenFeign 拦截器、MQ 生产者）调用，
     * 将当前的 W3C Trace Context 注入到 carrier（如 HTTP Header Map）中。
     * 传输中立，不绑定具体客户端。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.opentelemetry.api.OpenTelemetry")
    public TraceContextInjector traceContextInjector() {
        return new TraceContextInjector();
    }

    /**
     * 入站追踪过滤器。
     *
     * <p>从请求头提取 {@code traceparent}，创建 SERVER Span，写入 MDC，并向响应头回写。
     * 顺序设置为 {@code HIGHEST_PRECEDENCE + 10}。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.opentelemetry.api.OpenTelemetry")
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public TraceContextFilter traceContextFilter(ObservabilityProperties properties) {
        return new TraceContextFilter(properties.getTrace());
    }

    // ==================== Logging & Masking ====================

    @Bean
    @ConditionalOnMissingBean
    public BodyFieldMasker bodyFieldMasker(ObservabilityProperties properties) {
        return new BodyFieldMasker(properties.getLogging());
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryParamMasker queryParamMasker(ObservabilityProperties properties) {
        return new QueryParamMasker(properties.getLogging());
    }

    @Bean
    @ConditionalOnMissingBean
    public HeaderMasker headerMasker(ObservabilityProperties properties) {
        return new HeaderMasker(properties.getLogging());
    }

    /**
     * 统一脱敏入口。
     *
     * <p>通过构造函数收集容器中所有的 {@link DataMasker} 实现（包括业务模块自定义的扩展）。
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataMasker sensitiveDataMasker(
            ObjectProvider<List<DataMasker>> maskersProvider,
            ObservabilityProperties properties) {
        // ObjectProvider 避免因没有自定义 masker 时注入空列表报错
        List<DataMasker> maskers = maskersProvider.getIfAvailable(List::of);
        return new SensitiveDataMasker(maskers, properties.getLogging());
    }

    /**
     * 请求日志过滤器。
     *
     * <p>记录 method、脱敏后的 URI、状态码、耗时。
     * 顺序设置为 {@code HIGHEST_PRECEDENCE + 20}，在追踪过滤器之后执行。
     */
    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.HIGHEST_PRECEDENCE + 20)
    public RequestLoggingFilter requestLoggingFilter(
            SensitiveDataMasker masker,
            ObservabilityProperties properties) {
        return new RequestLoggingFilter(masker, properties.getLogging());
    }
}
