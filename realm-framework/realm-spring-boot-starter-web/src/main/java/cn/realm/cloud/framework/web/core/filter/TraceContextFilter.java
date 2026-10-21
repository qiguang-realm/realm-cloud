package cn.realm.cloud.framework.web.core.filter;

import cn.realm.cloud.framework.web.config.TraceContextProperties;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * W3C Trace Context 过滤器。
 *
 * <p><b>职责：</b>
 * <ol>
 *     <li>从请求头解析上游 {@code traceparent}（W3C Trace Context 标准），继承 trace-id</li>
 *     <li>若无有效上游上下文，由 OpenTelemetry SDK 生成符合 W3C 规范的 trace-id 与 span-id</li>
 *     <li>创建请求级 SERVER Span，并绑定到当前线程（{@link Span#makeCurrent()}）</li>
 *     <li>将 trace_id / span_id 写入 {@link MDC}，供日志框架输出（如 {@code %X{trace_id}}）</li>
 *     <li>将 {@code traceparent} 写入响应头，供客户端读取，便于问题排查</li>
 *     <li>请求结束后清理 MDC 并结束 Span，防止线程池复用导致上下文串号</li>
 * </ol>
 *
 * <p><b>OpenTelemetry 实例来源：</b>
 * 通过 {@link GlobalOpenTelemetry#get()} 获取，而非依赖注入。原因：
 * <ul>
 *     <li>挂载 OTel Java Agent 时，Agent 通过字节码增强设置 {@code GlobalOpenTelemetry}，
 *         但不会向 Spring 容器注册 {@code OpenTelemetry} Bean；</li>
 *     <li>使用 OTel SDK 自动配置时，{@code AutoConfiguredOpenTelemetrySdk.initialize()}
 *         内部会调用 {@code GlobalOpenTelemetry.set(...)}；</li>
 *     <li>两者都未启用时，{@link GlobalOpenTelemetry#get()} 返回 no-op 实例，
 *         不会抛异常。</li>
 * </ul>
 * 这种设计兼容“Agent 模式”、“SDK 模式”、“无追踪模式”三种部署场景。
 *
 * <p><b>为什么每次请求时动态获取，而不在构造时缓存？</b>
 * {@code GlobalOpenTelemetry} 可能在 Filter 构造之后才被设置：
 * <ul>
 *     <li>Java Agent 的初始化时机可能晚于 Spring 容器启动；</li>
 *     <li>测试场景中，SDK 通常在 {@code @BeforeAll} 或静态块里初始化，
 *         而 Filter 可能已被 Spring 容器提前创建；</li>
 *     <li>构造时缓存会永久持有 no-op 实例，导致 TraceId 无法继承上游。</li>
 * </ul>
 * 每次请求动态获取可确保拿到最新实例，代价仅是一次静态方法调用，可忽略。
 *
 * <p><b>与 Java Agent 的关系（重要）：</b>
 * 当应用同时挂载 OTel Java Agent 和本 Filter 时，Agent 会自动为 Servlet 创建
 * Server Span，导致双重 Span。解决方案：通过 {@code -Dotel.instrumentation.servlet.enabled=false}
 * 禁用 Agent 的 Servlet 埋点，让本 Filter 统一负责 Server Span 的创建。
 *
 * <p><b>W3C traceparent 格式：</b>
 * <pre>
 *   traceparent: VERSION-TRACE_ID-PARENT_SPAN_ID-TRACE_FLAGS
 *   示例:       00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
 * </pre>
 * 各字段长度：version=2位hex，trace-id=32位hex，span-id=16位hex，flags=2位hex。
 * 全部要求小写十六进制，且 trace-id 和 span-id 不能为全零。
 *
 * <p><b>MDC Key 说明：</b>
 * 使用 {@code trace_id} 和 {@code span_id}，与 OpenTelemetry 生态默认约定一致。
 * 如果同时挂载 Agent，Agent 也会向 MDC 写入相同 Key，值一致，不冲突。
 *
 * <p><b>异步 Servlet 场景：</b>
 * Servlet 3.0 的异步请求（{@code request.startAsync()}）会导致 MDC 在异步线程丢失。
 * 本实现覆盖同步请求场景。如需支持异步，建议配合 {@code TransmittableThreadLocal}
 * 或 Spring 的 {@code RequestContextFilter}。
 *
 * <h2>部署模式推荐</h2>
 *
 * <p><b>推荐使用 OTel Java Agent 模式</b>，理由如下：
 * <ul>
 *     <li><b>零代码侵入</b>：HTTP Client、JDBC、Redis、MQ 等下游调用自动埋点，
 *         无需在业务代码中引入任何 instrumentation 库；</li>
 *     <li><b>覆盖范围广</b>：官方维护数百个 instrumentation，持续更新，
 *         版本升级只需替换 agent jar；</li>
 *     <li><b>与 Filter 互补</b>：Filter 负责入口 SERVER Span，
 *         Agent 负责下游调用，通过一行参数禁用 Agent 的 Servlet 埋点即可避免双重 Span；</li>
 *     <li><b>配置解耦</b>：通过 JVM 参数或环境变量控制，应用代码不感知追踪实现。</li>
 * </ul>
 *
 * <p><b>仅在以下场景选择 SDK 模式：</b>
 * <ul>
 *     <li>无法控制 JVM 启动参数（如某些 Serverless / PaaS 环境不允许追加 {@code -javaagent}）；</li>
 *     <li>需要精细控制埋点范围，或自定义 Sampler、SpanProcessor、Exporter；</li>
 *     <li>团队已有成熟的 OpenTelemetry SDK 封装，不希望引入 Agent。</li>
 * </ul>
 * SDK 模式需要显式引入 {@code opentelemetry-sdk} 及对应的 instrumentation 库，
 * 并在应用启动时通过 {@code AutoConfiguredOpenTelemetrySdk.initialize()} 初始化。
 *
 * <h2>部署配置参考</h2>
 *
 * <p><b>模式一（推荐）：OTel Java Agent</b>
 * <pre>{@code
 * -javaagent:/path/to/opentelemetry-javaagent.jar
 * -Dotel.service.name=realm-app
 * -Dotel.exporter.otlp.endpoint=http://localhost:4317
 * -Dotel.traces.exporter=otlp
 * # 关键：禁用 Agent 的 Servlet 埋点，避免与 Filter 产生双重 Span
 * -Dotel.instrumentation.servlet.enabled=false
 * }</pre>
 *
 * <p><b>模式二：OTel SDK 自动配置</b>
 * <pre>{@code
 * OTEL_SERVICE_NAME=realm-app
 * OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
 * OTEL_TRACES_EXPORTER=otlp
 * OTEL_INSTRUMENTATION_SERVLET_ENABLED=false
 * }</pre>
 *
 * @author QI Guang
 * @see TraceContextProperties
 * @see GlobalOpenTelemetry
 * @since 1.0.0
 */
public class TraceContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceContextFilter.class);

    /**
     * MDC 中 trace_id 的 key（与 OpenTelemetry Java Agent 注入保持一致）。
     */
    public static final String TRACE_ID_MDC_KEY = "trace_id";

    /**
     * MDC 中 span_id 的 key。
     */
    public static final String SPAN_ID_MDC_KEY = "span_id";

    /**
     * W3C Trace Context 标准头部名称。
     */
    public static final String TRACEPARENT_HEADER = "traceparent";

    /**
     * OpenTelemetry Instrumentation 名称，用于标识本过滤器的 Span 来源。
     */
    private static final String INSTRUMENTATION_NAME = "cn.realm.cloud.framework.web";

    private final TraceContextProperties properties;

    public TraceContextFilter(TraceContextProperties properties) {
        this.properties = properties;
        log.info("[TraceContextFilter] 初始化完成，writeMdc={}, writeResponseHeader={}, readRequestHeader={}",
                properties.isWriteMdc(), properties.isWriteResponseHeader(), properties.isReadRequestHeader());
    }

    /**
     * 动态获取全局 OpenTelemetry 实例。
     *
     * <p>不缓存到字段，原因见类 Javadoc「为什么每次请求时动态获取」。
     *
     * @return 当前的 OpenTelemetry 实例；若未初始化则返回 no-op 实例
     */
    private OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }

    /**
     * 动态获取 Tracer 实例。
     *
     * <p>每次调用从当前 OpenTelemetry 实例派生，确保 SDK 后置初始化时仍能生效。
     */
    private Tracer tracer() {
        return openTelemetry().getTracer(INSTRUMENTATION_NAME);
    }

    /**
     * 过滤器核心逻辑。
     *
     * <p><b>执行流程：</b>
     * <pre>
     * 1. 从请求头提取上游 Context（W3C traceparent）
     *    ├── 存在且合法 → 继承上游 trace-id
     *    └── 不存在或不合法 → 由 SDK 生成新 trace-id
     * 2. 创建 SERVER Span 并绑定到当前线程
     * 3. 写入 MDC（trace_id + span_id）
     * 4. 写入响应头 traceparent
     * 5. 执行后续 Filter 链
     * 6. finally 清理 MDC + 结束 Span
     * </pre>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 每次请求时动态获取，避免构造时缓存 no-op 实例
        Tracer tracer = tracer();

        // 1. 从请求头提取上游 Context（W3C traceparent）
        Context parentContext = extractParentContext(request);

        // 2. 创建服务端 Span
        SpanBuilder spanBuilder = tracer.spanBuilder(request.getMethod() + " " + request.getRequestURI())
                .setSpanKind(SpanKind.SERVER)
                .setParent(parentContext);

        // 添加 HTTP 语义属性（OpenTelemetry Semantic Conventions）
        spanBuilder.setAttribute("http.method", request.getMethod());
        spanBuilder.setAttribute("http.url", request.getRequestURL().toString());
        spanBuilder.setAttribute("http.target", request.getRequestURI());
        spanBuilder.setAttribute("http.scheme", request.getScheme());
        spanBuilder.setAttribute("http.host", request.getHeader("Host"));

        Span span = spanBuilder.startSpan();

        // 3. 绑定到当前线程，后续代码可通过 Span.current() 读取
        try (Scope ignored = span.makeCurrent()) {

            SpanContext spanContext = span.getSpanContext();
            String traceId = spanContext.getTraceId();
            String spanId = spanContext.getSpanId();

            // 4. 写入 MDC，供 Logback 输出
            if (properties.isWriteMdc()) {
                MDC.put(TRACE_ID_MDC_KEY, traceId);
                MDC.put(SPAN_ID_MDC_KEY, spanId);
            }

            // 5. 写入响应头 traceparent
            if (properties.isWriteResponseHeader()) {
                String traceparent = buildTraceparent(spanContext);
                response.setHeader(TRACEPARENT_HEADER, traceparent);
                exposeCorsHeader(response, TRACEPARENT_HEADER);
            }

            try {
                filterChain.doFilter(request, response);
                setSpanStatusByHttpStatus(span, response.getStatus());
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

    /**
     * 从请求头提取上游 Trace Context。
     *
     * <p>使用 OpenTelemetry 全局传播器解析 {@code traceparent}，
     * 兼容 W3C Trace Context 标准，自动完成版本校验、格式校验、全零校验。
     * 若上游未传入或格式非法，返回 {@link Context#root()}，
     * 由后续 {@code spanBuilder} 生成新的 trace-id。
     */
    private Context extractParentContext(HttpServletRequest request) {
        if (!properties.isReadRequestHeader()) {
            return Context.root();
        }
        return openTelemetry().getPropagators()
                .getTextMapPropagator()
                .extract(Context.root(), request, HttpServletRequestGetter.INSTANCE);
    }

    /**
     * 构建 W3C traceparent 头部值。
     */
    private String buildTraceparent(SpanContext spanContext) {
        String flags = spanContext.isSampled() ? "01" : "00";
        return "00-" + spanContext.getTraceId() + "-" + spanContext.getSpanId() + "-" + flags;
    }

    /**
     * 根据 HTTP 状态码设置 Span 状态。
     *
     * <p>遵循 OpenTelemetry HTTP 语义约定：4xx / 5xx 标记为 ERROR，其余为 OK。
     */
    private void setSpanStatusByHttpStatus(Span span, int status) {
        if (status >= 400) {
            span.setStatus(StatusCode.ERROR, "HTTP " + status);
        } else {
            span.setStatus(StatusCode.OK);
        }
    }

    /**
     * 暴露 traceparent 给 CORS 场景的前端 JS 读取。
     */
    private void exposeCorsHeader(HttpServletResponse response, String headerName) {
        String existing = response.getHeader("Access-Control-Expose-Headers");
        if (existing == null || existing.isBlank()) {
            response.setHeader("Access-Control-Expose-Headers", headerName);
            return;
        }
        for (String h : existing.split(",")) {
            if (h.trim().equalsIgnoreCase(headerName)) {
                return;
            }
        }
        response.setHeader("Access-Control-Expose-Headers", existing + ", " + headerName);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 排除静态资源（减少无意义的 Span 创建与日志）
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
     * {@link HttpServletRequest} 的 {@link TextMapGetter} 实现。
     *
     * <p>供 OpenTelemetry 传播器从 HTTP 头部提取 trace context。
     */
    private enum HttpServletRequestGetter implements TextMapGetter<HttpServletRequest> {
        INSTANCE;

        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            if (carrier == null) {
                return Collections.emptyList();
            }
            return Collections.list(carrier.getHeaderNames());
        }

        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier == null ? null : carrier.getHeader(key);
        }
    }
}
