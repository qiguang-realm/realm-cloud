package cn.realm.cloud.framework.common.base.api;

import cn.realm.cloud.framework.common.enums.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Map;

/**
 * 统一响应静态工厂。
 *
 * <p>将 {@link ApiResponse}（业务契约）与 {@link ResponseEntity}（传输契约）结合，
 * 提供覆盖常见 HTTP 语义的静态工厂方法。
 *
 * <p><b>核心约定：</b>
 * <ul>
 *     <li><b>业务失败</b>（{@link #fail}）：HTTP 200 + 业务错误码。
 *         适用于校验失败、余额不足等"请求已正确处理，但业务未通过"的场景。</li>
 *     <li><b>HTTP 协议错误</b>（{@link #error}）：非 200 HTTP 状态码 + 业务错误码。
 *         适用于 404 资源不存在、403 无权限、401 未认证等场景。</li>
 * </ul>
 *
 * <p><b>REST 语义方法：</b>
 * <ul>
 *     <li>{@link #success(Object)} → 200 OK</li>
 *     <li>{@link #created(Object, URI)} → 201 Created（含 Location 头）</li>
 *     <li>{@link #accepted(String)} → 202 Accepted</li>
 *     <li>{@link #noContent()} → 204 No Content</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 成功
 * return Responses.success(user);
 *
 * // 创建资源（RFC 7231 §7.1.2：Location 必须是 URI）
 * return Responses.created(user, URI.create("/api/users/" + user.getId()));
 *
 * // 业务失败（HTTP 200 + 业务错误码）
 * return Responses.fail(ErrorCode.USER_NOT_FOUND);
 *
 * // HTTP 协议错误（404）
 * return Responses.notFound(ErrorCode.USER_NOT_FOUND);
 * }</pre>
 *
 * @author QI Guang
 * @see ApiResponse
 * @see ResponseBuilder
 * @see ErrorCode
 */
public final class Responses {

    private Responses() {
        throw new UnsupportedOperationException(
                "Responses is a static utility class and cannot be instantiated");
    }

    // ==================== 成功响应（HTTP 200） ====================

    /**
     * 构建 HTTP 200 成功响应，携带业务数据。
     */
    public static <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 构建 HTTP 200 成功响应，无业务数据。
     */
    public static <T> ResponseEntity<ApiResponse<T>> success() {
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 构建 HTTP 200 成功响应，携带自定义消息与业务数据。
     */
    public static <T> ResponseEntity<ApiResponse<T>> success(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    /**
     * 构建 HTTP 200 成功响应的 Builder（用于追加 HTTP 头）。
     */
    public static <T> ResponseBuilder<T> successBuilder(T data) {
        return new ResponseBuilder<>(ApiResponse.success(data));
    }

    // ==================== RESTful 语义响应 ====================

    /**
     * 构建 HTTP 201 Created 响应。
     */
    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    /**
     * 构建 HTTP 201 Created 响应，并设置 Location 头。
     *
     * <p><b>RFC 7231 §7.1.2：</b>Location 头类型为 URI-reference，
     * 因此参数类型为 {@link URI} 而非 {@link String}。
     *
     * @param data     创建的资源数据
     * @param location 资源位置（URI-reference，如 {@code URI.create("/api/users/1")}）
     */
    public static <T> ResponseEntity<ApiResponse<T>> created(T data, URI location) {
        return ResponseEntity.created(location).body(ApiResponse.success(data));
    }

    /**
     * 构建 HTTP 201 Created 响应的 Builder。
     */
    public static <T> ResponseBuilder<T> createdBuilder(T data) {
        return new ResponseBuilder<>(ApiResponse.success(data))
                .status(HttpStatus.CREATED);
    }

    /**
     * 构建 HTTP 201 Created 响应的 Builder，并设置 Location 头。
     */
    public static <T> ResponseBuilder<T> createdBuilder(T data, URI location) {
        return new ResponseBuilder<>(ApiResponse.success(data))
                .status(HttpStatus.CREATED)
                .withHeader(HttpHeaders.LOCATION, location.toString());
    }

    /**
     * 构建 HTTP 202 Accepted 响应（异步任务已接收）。
     */
    public static ResponseEntity<ApiResponse<Void>> accepted() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success());
    }

    /**
     * 构建 HTTP 202 Accepted 响应，携带提示消息。
     */
    public static ResponseEntity<ApiResponse<Void>> accepted(String message) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(message, null));
    }

    /**
     * 构建 HTTP 204 No Content 响应（无响应体）。
     */
    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    // ==================== 业务失败（HTTP 200 + 业务错误码） ====================

    /**
     * 构建业务失败响应（HTTP 200 + 业务错误码），使用 {@link ErrorCode} 默认消息。
     */
    public static <T> ResponseEntity<ApiResponse<T>> fail(ErrorCode errorCode) {
        return ResponseEntity.ok(ApiResponse.error(errorCode));
    }

    /**
     * 构建业务失败响应（HTTP 200 + 业务错误码），使用自定义消息。
     */
    public static <T> ResponseEntity<ApiResponse<T>> fail(ErrorCode errorCode, String customMessage) {
        return ResponseEntity.ok(ApiResponse.error(errorCode, customMessage));
    }

    /**
     * 构建业务失败响应的 Builder。
     */
    public static <T> ResponseBuilder<T> failBuilder(ErrorCode errorCode) {
        return new ResponseBuilder<>(ApiResponse.error(errorCode));
    }

    // ==================== HTTP 协议错误（非 200 状态码） ====================

    /**
     * 构建 HTTP 协议错误响应。
     */
    public static <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, ErrorCode errorCode) {
        return ResponseEntity.status(status).body(ApiResponse.error(errorCode));
    }

    /**
     * 构建 HTTP 协议错误响应，使用自定义消息。
     */
    public static <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, ErrorCode errorCode,
                                                           String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(errorCode, message));
    }

    /**
     * 构建 HTTP 404 Not Found 响应。
     */
    public static <T> ResponseEntity<ApiResponse<T>> notFound(ErrorCode errorCode) {
        return error(HttpStatus.NOT_FOUND, errorCode);
    }

    /**
     * 构建 HTTP 404 Not Found 响应，使用自定义消息。
     */
    public static <T> ResponseEntity<ApiResponse<T>> notFound(ErrorCode errorCode, String message) {
        return error(HttpStatus.NOT_FOUND, errorCode, message);
    }

    /**
     * 构建 HTTP 403 Forbidden 响应。
     */
    public static <T> ResponseEntity<ApiResponse<T>> forbidden(ErrorCode errorCode) {
        return error(HttpStatus.FORBIDDEN, errorCode);
    }

    /**
     * 构建 HTTP 401 Unauthorized 响应。
     */
    public static <T> ResponseEntity<ApiResponse<T>> unauthorized(ErrorCode errorCode) {
        return error(HttpStatus.UNAUTHORIZED, errorCode);
    }

    /**
     * 构建 HTTP 协议错误响应的 Builder。
     */
    public static ResponseBuilder<Void> errorBuilder(HttpStatus status, ErrorCode errorCode) {
        return new ResponseBuilder<>(ApiResponse.<Void>error(errorCode)).status(status);
    }

    /**
     * 构建 HTTP 协议错误响应的 Builder，携带业务数据。
     */
    public static <T> ResponseBuilder<T> errorBuilder(HttpStatus status, ErrorCode errorCode, T data) {
        return new ResponseBuilder<>(ApiResponse.of(errorCode.getCode(), errorCode.getMessage(), data))
                .status(status);
    }

    // ==================== 完全自定义 ====================

    /**
     * 完全自定义响应（HTTP 状态码与业务状态码一致）。
     */
    public static <T> ResponseEntity<ApiResponse<T>> of(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(ApiResponse.of(status.value(), message, data));
    }

    /**
     * 完全自定义响应（HTTP 状态码与业务状态码解耦）。
     */
    public static <T> ResponseEntity<ApiResponse<T>> custom(HttpStatus httpStatus, ErrorCode errorCode,
                                                            String message, T data) {
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.of(errorCode.getCode(), message, data));
    }

    // ==================== 带 HTTP 头的便捷方法 ====================

    /**
     * 为已有 {@link ApiResponse} 附加 HTTP 头，返回 HTTP 200。
     */
    public static <T> ResponseEntity<ApiResponse<T>> withHeaders(ApiResponse<T> response,
                                                                 HttpHeaders headers) {
        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * 为已有 {@link ApiResponse} 附加 HTTP 头，返回 HTTP 200。
     */
    public static <T> ResponseEntity<ApiResponse<T>> withHeaders(ApiResponse<T> response,
                                                                 Map<String, String> headersMap) {
        HttpHeaders headers = new HttpHeaders();
        if (headersMap != null) {
            headersMap.forEach(headers::add);
        }
        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * 为已有 {@link ApiResponse} 附加 HTTP 头，支持自定义状态码。
     */
    public static <T> ResponseEntity<ApiResponse<T>> withHeaders(ApiResponse<T> response,
                                                                 HttpStatus status,
                                                                 HttpHeaders headers) {
        return ResponseEntity.status(status).headers(headers).body(response);
    }
}
