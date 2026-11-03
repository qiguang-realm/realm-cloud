package cn.realm.cloud.framework.common.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * 当前追踪上下文访问器。
 *
 * <p>基于 W3C Trace Context 国际标准与 OpenTelemetry API，用于获取当前线程
 * 绑定的 TraceId 与 SpanId。
 *
 * <p>术语说明：
 * <ul>
 *   <li>TraceId：对应 W3C 字段 {@code trace-id}，OpenTelemetry Java API 为
 *       {@code getTraceId()}，英文术语写作 trace ID。</li>
 *   <li>SpanId：对应 W3C 字段 {@code span-id}，OpenTelemetry Java API 为
 *       {@code getSpanId()}。</li>
 *   <li>W3C Trace Context 是分布式追踪领域唯一的国际标准，标准化了 HTTP 头部
 *       {@code traceparent} 的格式，用于跨服务传播追踪上下文。</li>
 *   <li>OpenTelemetry 是该标准的主要实现者，SDK 默认使用密码学安全的随机数
 *       生成器生成 TraceId 和 SpanId。</li>
 * </ul>
 *
 * <p>使用前提：当前线程必须已绑定有效 Span（由 OpenTelemetry Java Agent 或
 * 手动创建的 Span 提供）。异步线程中需先通过 Context 传播机制传递 Span，
 * 否则返回空字符串。
 *
 * @author QI Guang
 */
public final class TraceContextAccessor {

    private TraceContextAccessor() {
        // 工具类禁止实例化
    }

    /**
     * 获取当前链路 TraceId。
     *
     * @return 32 位小写十六进制字符串；若当前无有效 Span，返回空字符串 {@code ""}，
     * 不会返回全零 ID
     */
    public static String currentTraceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : "";
    }

    /**
     * 获取当前 SpanId。
     *
     * @return 16 位小写十六进制字符串；若当前无有效 Span，返回空字符串 {@code ""}
     */
    public static String currentSpanId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getSpanId() : "";
    }
}
