package cn.realm.cloud.framework.web.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * W3C Trace Context 配置。
 *
 * <p><b>配置示例：</b>
 * <pre>
 * realm:
 *   web:
 *     trace:
 *       enabled: true
 *       read-request-header: true
 *       write-response-header: true
 *       write-mdc: true
 *       excluded-paths:
 *         - /actuator/**
 *         - /health
 * </pre>
 *
 * <p><b>术语说明：</b>
 * 采用 W3C Trace Context 标准术语，统一使用标准头部 {@code traceparent}，
 * 不使用自定义头部 {@code X-Trace-Id}。
 *
 * @author QI Guang
 */
@ConfigurationProperties(prefix = "realm.web.trace")
public class TraceContextProperties {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 是否启用 Trace Context 过滤器。
     *
     * <p>与 {@code TraceContextFilterAutoConfiguration} 上的
     * {@code @ConditionalOnProperty(matchIfMissing = true)} 读取同一个配置项。
     * 字段本身不被 Filter 读取，保留它是为了让
     * {@code spring-boot-configuration-processor} 生成完整的配置元数据，
     * 使 IDE 能够提示该配置项。
     */
    private boolean enabled = true;

    /**
     * 是否从请求头读取上游 {@code traceparent}（用于跨服务传递）。
     *
     * <p>开启后，若上游服务传递合法的 {@code traceparent}，将复用其 trace-id
     * 并继承链路；关闭则总是生成新的 trace context——<b>会打断链路</b>，
     * 仅在明确不需要跨服务追踪时关闭。
     */
    private boolean readRequestHeader = true;

    /**
     * 是否将 {@code traceparent} 写入响应头（供客户端读取）。
     *
     * <p>浏览器场景下，需要同时在 CORS 配置中暴露 {@code traceparent}
     * （{@code Access-Control-Expose-Headers}），否则前端 JS 无法读取。
     * 暴露动作由 {@code CorsConfigurer} 统一处理，本 Filter 不重复设置。
     */
    private boolean writeResponseHeader = true;

    /**
     * 是否将 trace_id / span_id 写入 MDC（供日志框架输出）。
     *
     * <p>写入的 MDC key 为 {@code trace_id} 和 {@code span_id}，
     * 与 OpenTelemetry Java Agent 的约定一致。应用需在日志格式中
     * 使用 {@code %X{trace_id}} / {@code %X{span_id}} 才能输出。
     */
    private boolean writeMdc = true;

    /**
     * 不参与追踪的路径列表（Ant 风格，使用 {@link AntPathMatcher} 匹配）。
     *
     * <p>默认排除静态资源与 Actuator 端点，避免无意义的 Span 产生，
     * 稀释业务 trace 的信噪比。需要追踪这些路径时，从默认值中移除对应项即可。
     */
    private List<String> excludedPaths = new ArrayList<>(List.of(
            "/**/*.ico",
            "/**/*.css",
            "/**/*.js",
            "/**/*.map",
            "/**/*.png",
            "/**/*.jpg",
            "/**/*.jpeg",
            "/**/*.gif",
            "/**/*.svg",
            "/**/*.woff",
            "/**/*.woff2",
            "/**/*.ttf",
            "/actuator/**"
    ));

    /**
     * 判断给定 URI 是否应跳过追踪。
     *
     * <p>同时受 {@link #enabled} 与 {@link #excludedPaths} 控制：
     * 总开关关闭时全部跳过；否则按 Ant 模式匹配。
     */
    public boolean isExcluded(String uri) {
        if (!enabled || uri == null) {
            return false;
        }
        return excludedPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isReadRequestHeader() {
        return readRequestHeader;
    }

    public void setReadRequestHeader(boolean readRequestHeader) {
        this.readRequestHeader = readRequestHeader;
    }

    public boolean isWriteResponseHeader() {
        return writeResponseHeader;
    }

    public void setWriteResponseHeader(boolean writeResponseHeader) {
        this.writeResponseHeader = writeResponseHeader;
    }

    public boolean isWriteMdc() {
        return writeMdc;
    }

    public void setWriteMdc(boolean writeMdc) {
        this.writeMdc = writeMdc;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }
}
