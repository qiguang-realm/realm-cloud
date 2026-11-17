package cn.realm.cloud.framework.web.trace;

import cn.realm.cloud.framework.web.properties.ObservabilityProperties;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Collections;

/**
 * W3C Trace Context 入站过滤器。
 *
 * @author QI Guang
 */
public class TraceContextFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "trace_id";
    public static final String SPAN_ID_MDC_KEY = "span_id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    private static final String INSTRUMENTATION_NAME = "cn.realm.cloud.framework.web";
    private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();

    private final ObservabilityProperties.Trace properties;

    public TraceContextFilter(ObservabilityProperties.Trace properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Tracer tracer = GlobalOpenTelemetry.get().getTracer(INSTRUMENTATION_NAME);
        Context parentContext = extractParentContext(request);

        Span span = tracer.spanBuilder(request.getMethod() + " " + request.getRequestURI())
                .setSpanKind(SpanKind.SERVER)
                .setParent(parentContext)
                .startSpan();

        span.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.getMethod());
        span.setAttribute(UrlAttributes.URL_FULL, request.getRequestURL().toString());
        span.setAttribute(UrlAttributes.URL_PATH, request.getRequestURI());
        span.setAttribute(UrlAttributes.URL_SCHEME, request.getScheme());
        span.setAttribute(ServerAttributes.SERVER_ADDRESS, request.getServerName());

        try (Scope ignored = span.makeCurrent()) {
            SpanContext spanContext = span.getSpanContext();

            if (properties.isWriteMdc()) {
                MDC.put(TRACE_ID_MDC_KEY, spanContext.getTraceId());
                MDC.put(SPAN_ID_MDC_KEY, spanContext.getSpanId());
            }

            if (properties.isWriteResponseHeader()) {
                String flags = spanContext.isSampled() ? "01" : "00";
                response.setHeader(TRACEPARENT_HEADER,
                        "00-" + spanContext.getTraceId() + "-" + spanContext.getSpanId() + "-" + flags);
            }

            try {
                filterChain.doFilter(request, response);
                if (response.getStatus() >= 500) {
                    span.setStatus(StatusCode.ERROR, "HTTP " + response.getStatus());
                }
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            } finally {
                if (properties.isWriteMdc()) {
                    MDC.remove(TRACE_ID_MDC_KEY);
                    MDC.remove(SPAN_ID_MDC_KEY);
                }
            }
        } finally {
            span.end();
        }
    }

    private Context extractParentContext(HttpServletRequest request) {
        if (!properties.isReadRequestHeader()) {
            return Context.root();
        }
        return GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.root(), request, HttpServletRequestGetter.INSTANCE);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = URL_PATH_HELPER.getPathWithinApplication(request);
        return properties.getExcludedPaths().stream().anyMatch(pattern ->
                org.springframework.util.AntPathMatcher.DEFAULT_PATH_SEPARATOR.equals(pattern) ||
                        new org.springframework.util.AntPathMatcher().match(pattern, path)
        );
    }

    private enum HttpServletRequestGetter implements TextMapGetter<HttpServletRequest> {
        INSTANCE;

        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            return carrier == null ? Collections.emptyList() : Collections.list(carrier.getHeaderNames());
        }

        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier == null ? null : carrier.getHeader(key);
        }
    }
}
