package cn.realm.cloud.framework.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TraceId 配置。
 *
 * <p><b>配置示例：</b>
 * <pre>
 * realm:
 *   web:
 *     trace-id:
 *       enabled: true
 *       header-name: X-Trace-Id
 *       read-request-header: true
 *       write-response-header: true
 * </pre>
 *
 * @author QI Guang
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "realm.web.trace-id")
public class TraceIdProperties {

    /**
     * 是否启用 TraceId 过滤器。
     */
    private boolean enabled = true;

    /**
     * TraceId 使用的 HTTP 头名称。
     *
     * <p>默认为业界惯例 {@code X-Trace-Id}；也可配置为
     * {@code traceparent}（W3C Trace Context 标准）。
     */
    private String headerName = "X-Trace-Id";

    /**
     * 是否从请求头读取上游 traceId（用于跨服务传递）。
     *
     * <p>开启后，若上游服务通过相同 header 传递 traceId，将被复用；
     * 关闭则总是生成新的 traceId（不推荐，会打断链路）。
     */
    private boolean readRequestHeader = true;

    /**
     * 是否将 traceId 写入响应头（供客户端读取）。
     */
    private boolean writeResponseHeader = true;

    // ==================== Getter / Setter ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
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
}
