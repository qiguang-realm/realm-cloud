package cn.realm.cloud.framework.web.core.filter;

import cn.realm.cloud.framework.web.config.TraceIdProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TraceId 生成与传递过滤器。
 *
 * <p><b>职责：</b>
 * <ol>
 *     <li>从请求头提取上游 traceId（如 {@code X-Trace-Id}），若无则生成</li>
 *     <li>将 traceId 写入 {@link MDC}，供日志框架输出（如 Logback 的 {@code %X{traceId}}）</li>
 *     <li>将 traceId 写入响应头，供客户端读取（便于问题排查）</li>
 *     <li>请求结束后清理 MDC，<b>防止线程池复用导致 traceId 串号</b></li>
 * </ol>
 *
 * <p><b>为什么继承 {@link OncePerRequestFilter}？</b>
 * <ul>
 *     <li>确保在请求转发（forward）或包含（include）时<b>只执行一次</b></li>
 *     <li>Spring 提供的基类，比原生 {@code Filter} 更符合 Spring MVC 生命周期</li>
 * </ul>
 *
 * <p><b>为什么 Order 是 {@code HIGHEST_PRECEDENCE}？</b>
 * <ul>
 *     <li>traceId 应<b>最早</b>生成，让后续所有 Filter（含 Spring Security）的日志都带上 traceId</li>
 *     <li>与 {@code GlobalExceptionHandler}（Order = +100）、{@code GlobalResponseBodyAdvice}（Order = +200）形成严格顺序</li>
 * </ul>
 *
 * <p><b>安全考虑（CWE-117 日志注入）：</b>
 * 上游传入的 traceId 会被<b>严格校验</b>——只允许 {@code [a-zA-Z0-9-]} 字符，
 * 防止 CRLF 注入或日志伪造。不合法时降级为服务端生成。
 *
 * <p><b>线程安全：</b>本类无状态，线程安全。
 *
 * <p><b>异步 Servlet 场景（重要）：</b>
 * Servlet 3.0 的异步请求（{@code request.startAsync()}）会导致 MDC 在异步线程丢失。
 * 如需支持，需配合 {@code TransmittableThreadLocal} 或 Spring 的
 * {@code RequestContextFilter}。当前实现覆盖<b>同步请求</b>场景。
 *
 * @author QI Guang
 * @since 1.0.0
 * @see TraceIdProperties
 */
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    /**
     * MDC 中 traceId 的 key（与 {@code GlobalExceptionHandler} 保持一致）。
     */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    /**
     * traceId 的合法字符校验（防 CRLF 注入，CWE-117）。
     * <p>允许：字母、数字、连字符；长度限制 8-64 位。
     */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");

    private final TraceIdProperties properties;

    public TraceIdFilter(TraceIdProperties properties) {
        this.properties = properties;
    }

    /**
     * 过滤器核心逻辑。
     *
     * <p><b>执行流程：</b>
     * <pre>
     * 1. 从请求头提取 traceId（若启用）
     *    ├── 存在且合法 → 使用上游 traceId
     *    └── 不存在或不合法 → 生成新 traceId
     * 2. 写入 MDC
     * 3. 写入响应头
     * 4. 执行后续 Filter 链
     * 5. finally 清理 MDC（关键！）
     * </pre>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        try {
            // 1. 写入 MDC（供日志框架使用）
            MDC.put(TRACE_ID_MDC_KEY, traceId);

            // 2. 写入响应头（供客户端读取）
            if (properties.isWriteResponseHeader()) {
                response.setHeader(properties.getHeaderName(), traceId);
                // CORS 场景：暴露给前端 JS 读取
                exposeCorsHeader(response, properties.getHeaderName());
            }

            // 3. 执行后续 Filter 链
            filterChain.doFilter(request, response);
        } finally {
            // 4. 清理 MDC（Tomcat 线程池复用必须清理，否则 traceId 串号）
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 解析 traceId：优先从请求头提取，失败则生成。
     *
     * @param request HTTP 请求
     * @return 合法的 traceId，永不为 {@code null}
     */
    private String resolveTraceId(HttpServletRequest request) {
        // 1. 尝试从请求头提取
        if (properties.isReadRequestHeader()) {
            String upstream = request.getHeader(properties.getHeaderName());
            if (upstream != null && !upstream.isBlank()) {
                if (TRACE_ID_PATTERN.matcher(upstream).matches()) {
                    if (log.isDebugEnabled()) {
                        log.debug("[resolveTraceId] use upstream traceId: {}", upstream);
                    }
                    return upstream;
                }
                // 不合法：记录 WARN 并降级为生成（不抛出异常，避免影响业务）
                log.warn("[resolveTraceId] invalid upstream traceId, will regenerate: {}",
                        abbreviate(upstream));
            }
        }

        // 2. 服务端生成
        return generateTraceId();
    }

    /**
     * 生成新的 traceId。
     *
     * <p>格式：{@code UUID} 去掉连字符，32 位十六进制。
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 暴露 {@code X-Trace-Id} 给 CORS 场景的前端 JS 读取。
     *
     * <p>根据 Fetch 规范，跨域请求的响应头默认不暴露给 JS（除 safelisted 头外）。
     * 若前端需通过 {@code response.headers.get('X-Trace-Id')} 读取，
     * 需服务器在 {@code Access-Control-Expose-Headers} 中声明。
     */
    private void exposeCorsHeader(HttpServletResponse response, String headerName) {
        String existing = response.getHeader("Access-Control-Expose-Headers");
        if (existing == null || existing.isBlank()) {
            response.setHeader("Access-Control-Expose-Headers", headerName);
            return;
        }
        // 已存在：检查是否已包含，避免重复
        for (String h : existing.split(",")) {
            if (h.trim().equalsIgnoreCase(headerName)) {
                return;
            }
        }
        response.setHeader("Access-Control-Expose-Headers", existing + ", " + headerName);
    }

    /**
     * 截断字符串用于日志，避免日志注入（CWE-117）。
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 64 ? text : text.substring(0, 64) + "...";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 排除静态资源（减少无意义的 traceId 生成与日志）
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return uri.endsWith(".ico")
                || uri.endsWith(".css")
                || uri.endsWith(".js")
                || uri.endsWith(".map")
                || uri.endsWith(".png")
                || uri.endsWith(".jpg")
                || uri.endsWith(".jpeg")
                || uri.endsWith(".gif")
                || uri.endsWith(".svg")
                || uri.endsWith(".woff")
                || uri.endsWith(".woff2")
                || uri.endsWith(".ttf");
    }

    /**
     * 过滤器顺序：最高优先级，确保 traceId 最早生成。
     */
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
