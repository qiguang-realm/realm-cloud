package cn.realm.cloud.framework.web.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Realm Web 全局配置（前缀 {@code realm.web}）。
 *
 * <p><b>职责边界：</b>本类只承载跨子域的全局开关与 Web 默认安全策略
 * （CORS、安全响应头）。领域级配置归入各自领域包：
 * <ul>
 *     <li>{@code realm.web.trace.*} → {@code TraceContextProperties}（{@code trace/} 包）</li>
 *     <li>{@code realm.web.logging.*} → {@code LoggingProperties}（{@code logging/} 包）</li>
 *     <li>{@code realm.web.pagination.*} → {@code PaginationProperties}（{@code pagination/} 包）</li>
 *     <li>{@code realm.web.error.*} → {@code ErrorProperties}（{@code exception/} 包）</li>
 * </ul>
 * 把领域配置放在全局 {@code properties/} 下，会随能力增长演变成“配置坟场”；
 * 各领域配置与其服务的横切逻辑放在同一包内，导航与内聚都更好。
 *
 * <p><b>配置示例：</b>
 * <pre>
 * realm:
 *   web:
 *     enabled: true
 *     cors:
 *       # 默认拒绝跨域；生产环境显式声明来源
 *       allowed-origins:
 *         - https://app.example.com
 *       allowed-methods:
 *         - GET
 *         - POST
 *         - PUT
 *         - DELETE
 *       allow-credentials: false
 *       max-age: 3600
 *       # 默认暴露 W3C Trace Context，供前端读取 traceId
 *       # exposed-headers 一般不需要配置，默认已包含 traceparent / tracestate
 *     security-headers:
 *       enabled: true
 * </pre>
 *
 * <p><b>为什么 CORS 默认拒绝：</b>
 * {@code Access-Control-Allow-Origin: *} 与 {@code allowCredentials=true} 的组合
 * 会被浏览器直接拒绝，但更常见的风险是开发者为了“先跑通”随手放开白名单，
 * 上线后忘记收紧。默认拒绝、显式声明，让“不配置”成为最安全的姿态，
 * 与 OWASP CORS 安全实践一致。
 *
 * <p><b>为什么默认暴露 {@code traceparent} / {@code tracestate}：</b>
 * 前端需要读取响应头中的 W3C Trace Context 才能把 traceId 带进用户侧日志。
 * {@code Access-Control-Expose-Headers} 的写入属于 CORS 配置的职责，
 * 不应由 {@code TraceContextFilter} 手动拼接——那会与 Spring 的 CORS 处理
 * 顺序不确定，多个 Filter 同时写同一头时互相覆盖。
 *
 * @author QI Guang
 * @see cn.realm.cloud.framework.web.autoconfigure.WebDefaultsAutoConfiguration
 */
@ConfigurationProperties(prefix = "realm.web")
public class WebProperties {

    /**
     * 全局开关。{@code false} 时 {@code WebAutoConfiguration} 不加载，
     * 所有 Web 横切能力（异常映射、追踪、日志脱敏、分页绑定、版本解析、
     * CORS 与安全头）整体退让。
     *
     * <p>与 {@code WebAutoConfiguration} 上的
     * {@code @ConditionalOnProperty(prefix = "realm.web", name = "enabled",
     * havingValue = "true", matchIfMissing = true)} 读取同一个配置项；
     * 字段本身不被任何 Bean 读取，保留它是为了让
     * {@code spring-boot-configuration-processor} 生成完整的配置元数据，
     * 使 IDE 能够提示该配置项。
     */
    private boolean enabled = true;

    /**
     * CORS 配置。默认拒绝跨域，白名单或模式列表为空时不注册任何映射。
     */
    private Cors cors = new Cors();

    /**
     * 安全响应头配置。默认开启。
     */
    private SecurityHeaders securityHeaders = new SecurityHeaders();

    // ==================== 嵌套配置类 ====================

    /**
     * CORS 配置。
     *
     * <p><b>{@code allowedOrigins} 与 {@code allowedOriginPatterns} 的区别：</b>
     * <ul>
     *     <li>{@code allowedOrigins}：精确匹配，不支持通配；
     *         配置 {@code *} 且 {@code allowCredentials=true} 时 Spring 会抛异常</li>
     *     <li>{@code allowedOriginPatterns}：支持通配模式（如
     *         {@code https://*.example.com}），Spring 5.3+ 允许其在
     *         {@code allowCredentials=true} 下使用——通过返回具体 Origin 而非
     *         {@code *} 来满足规范</li>
     * </ul>
     * 需要通配来源且携带凭证时，使用 {@code allowedOriginPatterns}，
     * 而不是 {@code allowedOrigins: ["*"]}。
     *
     * <p><b>校验：</b>{@link #isWildcardWithCredentials()} 在配置层提前暴露
     * 危险组合，让 {@code CorsConfigurer} 在启动时抛出清晰的异常，
     * 而不是等到浏览器端才发现跨域失败。
     */
    public static class Cors {

        /**
         * 允许的跨域来源（精确匹配）。默认为空——不开放任何跨域来源。
         *
         * <p>生产环境应显式列出前端域名；开发环境可通过 profile 覆盖。
         * 不要使用 {@code *}——如需通配，请使用 {@link #allowedOriginPatterns}。
         */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * 允许的跨域来源模式（支持通配，如 {@code https://*.example.com}）。
         * 默认为空。与 {@link #allowedOrigins} 互为补充：
         * 精确来源放 {@code allowedOrigins}，通配来源放 {@code allowedOriginPatterns}。
         *
         * <p>Spring 5.3+ 允许 {@code allowedOriginPatterns} 与
         * {@code allowCredentials=true} 共存，具体做法是对每个请求返回具体的
         * Origin 而非 {@code *}，符合 CORS 规范。
         */
        private List<String> allowedOriginPatterns = new ArrayList<>();

        /**
         * 允许的 HTTP 方法。默认只允许常用方法；
         * {@code OPTIONS} 由 Spring MVC 的预检处理自动响应，无需在此列出。
         */
        private List<String> allowedMethods =
                new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));

        /**
         * 允许的请求头。默认 {@code *}——允许所有请求头。
         *
         * <p>跨域请求的 {@code Authorization}、{@code Content-Type} 等头部
         * 需要被允许，否则预检会失败。使用 {@code *} 是最常见的配置；
         * 如果团队有更严格的审计要求，可以改为显式白名单。
         */
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        /**
         * 暴露给浏览器端 JS 的响应头。默认暴露 W3C Trace Context 两个头，
         * 供前端把 traceId 带进用户侧日志。
         *
         * <p>一般不需要覆盖此列表；如需追加自定义头，可通过配置追加，
         * {@code CorsConfigurer} 会把配置值与默认值合并。
         */
        private List<String> exposedHeaders =
                new ArrayList<>(List.of("traceparent", "tracestate"));

        /**
         * 是否允许携带凭证（Cookie、Authorization 等）。
         * 默认 {@code false}——只有显式开启才允许。
         *
         * <p>开启后，{@code allowedOrigins} 不能包含 {@code *}；
         * 如需通配来源，请使用 {@code allowedOriginPatterns}。
         * 启动时由 {@link #isWildcardWithCredentials()} 校验并抛出清晰异常。
         */
        private boolean allowCredentials = false;

        /**
         * 预检结果缓存时间（秒）。默认 3600 秒（1 小时）。
         * 过短会频繁触发预检请求，过长会让 CORS 策略变更延迟生效。
         */
        private long maxAge = 3600;

        /**
         * 校验危险组合：{@code allowedOrigins} 含 {@code *} 且
         * {@code allowCredentials=true}。CORS 规范禁止此组合；
         * Spring 的 {@code CorsRegistry} 会在注册时抛
         * {@code IllegalArgumentException}，但异常信息不够直接。
         * 在配置层提前校验，可以让 {@code CorsConfigurer} 抛出
         * 指向配置项的清晰异常。
         *
         * @return 是否构成“通配来源 + 携带凭证”的危险组合
         */
        public boolean isWildcardWithCredentials() {
            return allowCredentials
                    && allowedOrigins != null
                    && allowedOrigins.contains("*");
        }

        /**
         * 是否配置了任何来源（精确或模式）。
         * 全空时不注册 CORS 映射，即默认拒绝跨域。
         */
        public boolean hasAnyOrigin() {
            return (allowedOrigins != null && !allowedOrigins.isEmpty())
                    || (allowedOriginPatterns != null && !allowedOriginPatterns.isEmpty());
        }

        // ==================== Getter / Setter ====================

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(long maxAge) {
            this.maxAge = maxAge;
        }
    }

    /**
     * 安全响应头配置。
     *
     * <p>默认开启。安全头的具体值由 {@code SecurityHeadersConfigurer} 决定，
     * 本类只控制开关——这样调整具体头值不需要改配置属性类，
     * 符合“配置类只承载语义开关，不承载策略细节”的边界。
     */
    public static class SecurityHeaders {

        /**
         * 是否启用安全响应头过滤器。默认 {@code true}。
         *
         * <p>仅在应用已有等效安全头机制（Spring Security 的
         * {@code HeadersConfigurer}、网关统一注入）时关闭，
         * 避免两套机制并行写同一组头，导致审计时无法确定安全头的唯一来源。
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public SecurityHeaders getSecurityHeaders() {
        return securityHeaders;
    }

    public void setSecurityHeaders(SecurityHeaders securityHeaders) {
        this.securityHeaders = securityHeaders;
    }
}
