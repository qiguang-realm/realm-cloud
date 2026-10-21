package cn.realm.cloud.framework.web.core.filter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link TraceContextFilter} 集成测试。
 *
 * <p>验证 W3C Trace Context 的端到端传播：
 * <ol>
 *     <li>上游传入 {@code traceparent}，响应头与响应体中的 traceId 应与上游一致</li>
 *     <li>上游未传入 {@code traceparent}，应生成新的 traceId 并写入响应头</li>
 *     <li>上游传入非法 {@code traceparent}，应降级为生成新 traceId</li>
 *     <li>上游传入全零 {@code traceparent}，应识别为非法并降级</li>
 * </ol>
 *
 * <p><b>关键点：</b>测试启动时初始化真实 OpenTelemetry SDK，
 * 并注册到 {@link GlobalOpenTelemetry}，否则 Filter 拿到的是 no-op 实例，
 * Span 无效，测试会“通过但无意义”。
 *
 * <p><b>为什么要显式设置 W3C 传播器？</b>
 * {@code OpenTelemetrySdk.builder()} 的默认传播器是 {@code ContextPropagators.noop()}，
 * 即不解析任何请求头。如果不显式设置 {@link W3CTraceContextPropagator}，
 * Filter 调用 {@code getPropagators().getTextMapPropagator().extract(...)}
 * 会返回传入的原始 Context，上游 {@code traceparent} 被忽略，
 * 导致服务端总是生成新的 trace-id，而不会继承上游。
 *
 * <p>生产环境无此问题：
 * <ul>
 *     <li>Agent 模式：OTel Java Agent 默认带 W3C 传播器；</li>
 *     <li>SDK 自动配置模式：{@code AutoConfiguredOpenTelemetrySdk.initialize()}
 *         默认使用 W3C 传播器；</li>
 *     <li>仅手动构建 SDK 时才需要显式设置。</li>
 * </ul>
 */
@SpringBootTest(classes = TraceContextFilterIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "realm.web.trace-context.enabled=true",
        "realm.web.trace-context.read-request-header=true",
        "realm.web.trace-context.write-response-header=true",
        "realm.web.trace-context.write-mdc=true"
})
class TraceContextFilterIntegrationTest {

    /**
     * 测试用固定 traceId（32 位小写 hex，符合 W3C 规范）。
     */
    private static final String UPSTREAM_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    /**
     * 测试用固定 spanId（16 位小写 hex）。
     */
    private static final String UPSTREAM_SPAN_ID = "00f067aa0ba902b7";

    /**
     * 合法 traceparent。
     */
    private static final String VALID_TRACEPARENT =
            "00-" + UPSTREAM_TRACE_ID + "-" + UPSTREAM_SPAN_ID + "-01";

    /**
     * 全零 traceparent（W3C 规范规定 trace-id 与 span-id 不能为全零）。
     */
    private static final String ALL_ZERO_TRACEPARENT =
            "00-00000000000000000000000000000000-0000000000000000-01";

    private static InMemorySpanExporter spanExporter;
    private static SdkTracerProvider tracerProvider;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void setUpOpenTelemetry() {
        // 1. 初始化 Span 导出器（内存版，用于断言）
        spanExporter = InMemorySpanExporter.create();

        // 2. 构建 SdkTracerProvider
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        // 3. 构建 OpenTelemetrySdk，并显式设置 W3C 传播器
        //    如果不设置，默认是 ContextPropagators.noop()，将无法解析上游 traceparent
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        // 4. 注册到 GlobalOpenTelemetry（Filter 通过 GlobalOpenTelemetry.get() 获取）
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(sdk);
    }

    @AfterAll
    static void tearDownOpenTelemetry() {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        GlobalOpenTelemetry.resetForTest();
    }

    // ================================================================
    // 测试用例
    // ================================================================

    /**
     * 场景一：上游传入合法 traceparent。
     *
     * <p>期望：
     * <ul>
     *     <li>响应头 {@code traceparent} 中的 trace-id 与上游一致</li>
     *     <li>响应体 {@code traceId} 字段与上游一致</li>
     *     <li>服务端新建的 SERVER Span 的 trace-id 与上游一致</li>
     *     <li>服务端 Span 的 parent span-id 为上游传入的 span-id</li>
     * </ul>
     */
    @Test
    void shouldPropagateTraceIdFromUpstream() throws Exception {
        spanExporter.reset();

        mockMvc.perform(get("/test/trace")
                        .header("traceparent", VALID_TRACEPARENT))
                .andExpect(status().isOk())
                .andExpect(header().exists("traceparent"))
                .andExpect(jsonPath("$.data.traceId").value(UPSTREAM_TRACE_ID))
                .andExpect(jsonPath("$.data.spanId").isNotEmpty());

        // 断言服务端 Span 的 trace-id 与上游一致
        var spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        SpanContext serverSpanContext = spans.get(0).getSpanContext();
        assertThat(serverSpanContext.getTraceId()).isEqualTo(UPSTREAM_TRACE_ID);
        // 服务端 Span 的 parent 应为上游的 span-id
        assertThat(spans.get(0).getParentSpanId()).isEqualTo(UPSTREAM_SPAN_ID);
    }

    /**
     * 场景二：上游未传入 traceparent。
     *
     * <p>期望：
     * <ul>
     *     <li>响应头 {@code traceparent} 存在，且 trace-id 为 32 位小写 hex</li>
     *     <li>trace-id 不为全零</li>
     *     <li>span-id 为 16 位小写 hex</li>
     *     <li>flags 为 2 位小写 hex</li>
     * </ul>
     */
    @Test
    void shouldGenerateNewTraceIdWhenUpstreamAbsent() throws Exception {
        spanExporter.reset();

        String responseTraceparent = mockMvc.perform(get("/test/trace"))
                .andExpect(status().isOk())
                .andExpect(header().exists("traceparent"))
                .andReturn()
                .getResponse()
                .getHeader("traceparent");

        assertThat(responseTraceparent).isNotNull();

        // 解析响应头 traceparent
        String[] parts = responseTraceparent.split("-");
        assertThat(parts).hasSize(4);
        assertThat(parts[0]).isEqualTo("00");

        String traceId = parts[1];
        String spanId = parts[2];
        String flags = parts[3];

        // trace-id 格式校验：32 位小写 hex，且不为全零
        assertThat(traceId).matches("^[0-9a-f]{32}$");
        assertThat(traceId).isNotEqualTo("00000000000000000000000000000000");
        // span-id 格式校验：16 位小写 hex
        assertThat(spanId).matches("^[0-9a-f]{16}$");
        // flags 格式校验：2 位小写 hex
        assertThat(flags).matches("^[0-9a-f]{2}$");
    }

    /**
     * 场景三：上游传入非法 traceparent。
     *
     * <p>期望：降级为新生成，不抛异常。
     */
    @Test
    void shouldRegenerateWhenUpstreamInvalid() throws Exception {
        spanExporter.reset();

        String invalidTraceparent = "invalid-traceparent-value";

        String responseTraceparent = mockMvc.perform(get("/test/trace")
                        .header("traceparent", invalidTraceparent))
                .andExpect(status().isOk())
                .andExpect(header().exists("traceparent"))
                .andReturn()
                .getResponse()
                .getHeader("traceparent");

        assertThat(responseTraceparent).isNotNull();
        String[] parts = responseTraceparent.split("-");
        // 生成的 trace-id 不应等于非法输入，且符合 32 位 hex 格式
        assertThat(parts[1]).isNotEqualTo(invalidTraceparent);
        assertThat(parts[1]).matches("^[0-9a-f]{32}$");
    }

    /**
     * 场景四：上游传入全零 traceparent。
     *
     * <p>期望：全零 trace-id 被识别为非法，降级为新生成。
     */
    @Test
    void shouldRegenerateWhenTraceIdAllZero() throws Exception {
        spanExporter.reset();

        String responseTraceparent = mockMvc.perform(get("/test/trace")
                        .header("traceparent", ALL_ZERO_TRACEPARENT))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("traceparent");

        assertThat(responseTraceparent).isNotNull();
        String traceId = responseTraceparent.split("-")[1];
        assertThat(traceId).isNotEqualTo("00000000000000000000000000000000");
        assertThat(traceId).matches("^[0-9a-f]{32}$");
    }

    // ================================================================
    // 测试用 Spring Boot 应用 + Controller
    // ================================================================

    /**
     * 最小化测试用 Spring Boot 应用。
     *
     * <p>通过 {@code @SpringBootApplication} 触发自动配置扫描，
     * 使 {@code TraceContextFilterAutoConfiguration} 生效。
     */
    @SpringBootApplication
    static class TestApplication {

        /**
         * 测试用 Controller，返回当前 traceId / spanId，供断言。
         */
        @RestController
        static class TraceTestController {

            @GetMapping("/test/trace")
            public Map<String, String> trace() {
                SpanContext context = Span.current().getSpanContext();
                return Map.of(
                        "traceId", context.getTraceId(),
                        "spanId", context.getSpanId()
                );
            }
        }
    }
}
