package cn.realm.cloud.framework.web.logging;

import cn.realm.cloud.framework.web.properties.ObservabilityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求日志记录过滤器。
 *
 * <p><b>职责：</b>记录请求方法、<b>脱敏后的</b> URI、响应状态码和处理耗时。
 *
 * <p><b>安全依据：</b>绝不能记录原始的 Request URI，必须将其传递给
 * {@link SensitiveDataMasker#maskUri(String)} 进行处理，防止 Query 参数泄漏。
 *
 * <p><b>日志格式：</b>输出符合结构化日志的规范（JSON 字段容易提取，
 * 且通常配合 MDC 中的 {@code trace_id} 一起输出）。
 *
 * @author QI Guang
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final SensitiveDataMasker masker;
    private final ObservabilityProperties.Logging properties;

    public RequestLoggingFilter(SensitiveDataMasker masker, ObservabilityProperties.Logging properties) {
        this.masker = masker;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (properties.isEnabled()) {
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                // 1. 获取完整 URI（包含 query string）
                String rawUri = request.getRequestURI();
                if (request.getQueryString() != null) {
                    rawUri += "?" + request.getQueryString();
                }

                // 2. 关键：进行脱敏处理
                String safeUri = masker.maskUri(rawUri);

                // 3. 记录日志（如果 MDC 中有 trace_id，会由 Logback 的 pattern 自动拼接）
                log.info("{} {} status={} elapsed={}ms",
                        request.getMethod(), safeUri, response.getStatus(), elapsedMs);
            }
        }
    }

    /**
     * 排除静态资源和 Actuator 端点，减少日志噪音。
     * 追踪过滤器中已经排除过一次，这里为保持独立性也做一次排除。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return uri.endsWith(".ico") || uri.endsWith(".css") || uri.endsWith(".js") || uri.endsWith(".map")
                || uri.endsWith(".png") || uri.endsWith(".jpg") || uri.endsWith(".jpeg") || uri.endsWith(".gif")
                || uri.endsWith(".svg") || uri.endsWith(".woff") || uri.endsWith(".woff2") || uri.endsWith(".ttf")
                || uri.startsWith("/actuator/");
    }
}
