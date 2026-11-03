package cn.realm.cloud.framework.common.base.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Objects;

/**
 * 响应构建器（Builder），用于在 {@link ApiResponse} 基础上附加 HTTP 状态码与响应头。
 *
 * <p><b>职责边界：</b>本类只负责"给已有 {@code ApiResponse} 追加传输层信息"，
 * 静态工厂方法请使用 {@link Responses}。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * return Responses.successBuilder(user)
 *         .withHeader("X-Custom", "value")
 *         .build();
 * }</pre>
 *
 * <p><b>线程安全性：</b>本类<b>非线程安全</b>，每次 HTTP 请求应创建新实例，
 * 不应跨请求共享。
 *
 * @param <T> 业务数据类型
 * @author QI Guang
 * @see Responses
 * @see ApiResponse
 */
public final class ResponseBuilder<T> {

    // ==================== 成员变量 ====================

    private final ApiResponse<T> apiResponse;

    private final HttpHeaders headers = new HttpHeaders();

    private HttpStatus httpStatus = HttpStatus.OK;

    // ==================== 构造器 ====================

    /**
     * 构造一个 ResponseBuilder（包级可见，仅由 {@link Responses} 调用）。
     *
     * @param apiResponse 已构建的 {@link ApiResponse}
     */
    ResponseBuilder(ApiResponse<T> apiResponse) {
        this.apiResponse = Objects.requireNonNull(apiResponse, "apiResponse must not be null");
    }

    // ==================== 链式方法 ====================

    /**
     * 设置 HTTP 状态码。
     *
     * @param status HTTP 状态，不允许为 {@code null}
     */
    public ResponseBuilder<T> status(HttpStatus status) {
        this.httpStatus = Objects.requireNonNull(status, "HttpStatus must not be null");
        return this;
    }

    /**
     * 添加单个 HTTP 头。若 {@code value} 为 {@code null}，则忽略本次调用。
     */
    public ResponseBuilder<T> withHeader(String name, String value) {
        if (value != null) {
            this.headers.set(name, value);
        }
        return this;
    }

    /**
     * 添加多个 HTTP 头。{@code null} 值会被忽略。
     */
    public ResponseBuilder<T> withHeaders(Map<String, String> headersMap) {
        if (headersMap != null) {
            headersMap.forEach(this::withHeader);
        }
        return this;
    }

    /**
     * 合并 Spring {@link HttpHeaders}。
     */
    public ResponseBuilder<T> withHeaders(HttpHeaders headers) {
        if (headers != null) {
            this.headers.putAll(headers);
        }
        return this;
    }

    /**
     * 构建最终的 {@link ResponseEntity}。
     */
    public ResponseEntity<ApiResponse<T>> build() {
        return ResponseEntity.status(httpStatus).headers(headers).body(apiResponse);
    }
}
