package cn.realm.cloud.framework.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * W3C Trace Context 配置。
 *
 * <p><b>配置示例：</b>
 * <pre>
 * realm:
 *   web:
 *     trace-context:
 *       enabled: true
 *       read-request-header: true
 *       write-response-header: true
 *       exclude-paths:
 *         - /actuator/**
 *         - /health
 * </pre>
 *
 * <p><b>术语说明：</b>
 * 采用 W3C Trace Context 标准术语，不再使用自定义头部 {@code X-Trace-Id}，
 * 统一使用标准头部 {@code traceparent}。
 *
 * @author QI Guang
 */
@ConfigurationProperties(prefix = "realm.web.trace-context")
public class TraceContextProperties {

    /**
     * 是否启用 Trace Context 过滤器。
     */
    private boolean enabled = true;

    /**
     * 是否从请求头读取上游 {@code traceparent}（用于跨服务传递）。
     *
     * <p>开启后，若上游服务传递合法的 {@code traceparent}，
     * 将复用其 trace-id 并继承链路；关闭则总是生成新的 trace context（不推荐，会打断链路）。
     */
    private boolean readRequestHeader = true;

    /**
     * 是否将 {@code traceparent} 写入响应头（供客户端读取）。
     */
    private boolean writeResponseHeader = true;

    /**
     * 是否将 trace_id / span_id 写入 MDC（供日志框架输出）。
     */
    private boolean writeMdc = true;

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
}
