package cn.realm.cloud.framework.common.base.api;

import cn.realm.cloud.framework.common.enums.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 响应构建器（Builder），用于快速构建带有 {@link ApiResponse} 体的 {@link ResponseEntity}。
 *
 * <p>将统一响应体与 Spring HTTP 响应对象结合，既保留了业务状态码的独立性，
 * 又能灵活控制 HTTP 状态、Headers 等底层传输细节。
 *
 * <p>设计理念：
 * <ul>
 *   <li><b>数据契约</b>：{@link ApiResponse} 承载业务数据（code、message、data、traceId）</li>
 *   <li><b>传输契约</b>：{@link ResponseEntity} 承载 HTTP 协议信息（状态码、Headers）</li>
 *   <li><b>契约桥梁</b>：{@code ResponseBuilder} 将两者优雅结合</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 1. 简单成功响应（HTTP 200）
 * return ResponseBuilder.success(user);
 *
 * // 2. 创建资源（HTTP 201）
 * return ResponseBuilder.created(user);
 *
 * // 3. 无内容（HTTP 204）
 * return ResponseBuilder.noContent();
 *
 * // 4. 业务错误（HTTP 200，但业务状态码为错误码）
 * return ResponseBuilder.fail(ErrorCode.USER_NOT_FOUND);
 *
 * // 5. HTTP 协议错误（如 404）
 * return ResponseBuilder.error(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND);
 *
 * // 6. 带自定义响应头
 * return ResponseBuilder.success(user)
 *         .withHeader("X-Custom-Header", "value")
 *         .build();
 * </pre>
 *
 * @author QI Guang
 * @see ApiResponse
 * @see ResponseEntity
 * @see ErrorCode
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class ResponseBuilder<T> {

    // ==================== 成员变量 ====================

    private final ApiResponse<T> apiResponse;
    private final HttpHeaders headers = new HttpHeaders();
    private HttpStatus httpStatus = HttpStatus.OK;

    // ==================== 私有构造器 ====================

    private ResponseBuilder(ApiResponse<T> apiResponse) {
        this.apiResponse = apiResponse;
    }

    /**
     * 获取当前构建器实例（用于链式调用）
     *
     * @param response 已构建的 ApiResponse
     * @param <T>      数据类型
     * @return ResponseBuilder 实例
     */
    public static <T> ResponseBuilder<T> from(ApiResponse<T> response) {
        return new ResponseBuilder<>(response);
    }

    // ==================== 链式方法 ====================

    /**
     * 设置 HTTP 状态码
     *
     * @param status HTTP 状态枚举
     * @return 当前构建器
     */
    public ResponseBuilder<T> status(HttpStatus status) {
        this.httpStatus = Objects.requireNonNull(status, "HttpStatus must not be null");
        return this;
    }

    /**
     * 添加单个 HTTP 头
     *
     * @param name  头名称
     * @param value 头值
     * @return 当前构建器
     */
    public ResponseBuilder<T> withHeader(String name, String value) {
        if (value != null) {
            this.headers.set(name, value);
        }
        return this;
    }

    /**
     * 添加多个 HTTP 头
     *
     * @param headersMap 头键值对
     * @return 当前构建器
     */
    public ResponseBuilder<T> withHeaders(Map<String, String> headersMap) {
        if (headersMap != null) {
            headersMap.forEach((key, value) -> {
                if (value != null) {
                    this.headers.add(key, value);
                }
            });
        }
        return this;
    }

    /**
     * 添加 Spring HttpHeaders 对象
     *
     * @param headers Spring HttpHeaders
     * @return 当前构建器
     */
    public ResponseBuilder<T> withHeaders(HttpHeaders headers) {
        if (headers != null) {
            this.headers.addAll(headers);
        }
        return this;
    }

    /**
     * 构建最终的 ResponseEntity
     *
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public ResponseEntity<ApiResponse<T>> build() {
        return ResponseEntity.status(httpStatus).headers(headers).body(apiResponse);
    }

    // ==================== 静态工厂方法：成功响应（HTTP 200） ====================

    /**
     * 构建一个 HTTP 200 的成功响应，携带业务数据。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return ResponseEntity 包裹的 ApiResponse，状态码 200 OK
     */
    public static <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 构建一个 HTTP 200 的成功响应，不带业务数据。
     *
     * @param <T> 数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse，状态码 200 OK
     */
    public static <T> ResponseEntity<ApiResponse<T>> success() {
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 构建一个 HTTP 200 的成功响应，携带自定义消息和业务数据。
     *
     * @param message 自定义成功消息
     * @param data    业务数据
     * @param <T>     数据类型
     * @return ResponseEntity 包裹的 ApiResponse，状态码 200 OK
     */
    public static <T> ResponseEntity<ApiResponse<T>> success(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    /**
     * 构建一个 HTTP 200 的成功响应，使用链式构建器（便于添加 Header）
     * <p>
     * 使用示例：
     * <pre>
     * return ResponseBuilder.successBuilder(user)
     *         .withHeader("X-Custom", "value")
     *         .build();
     * </pre>
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return ResponseBuilder 实例
     */
    public static <T> ResponseBuilder<T> successBuilder(T data) {
        return new ResponseBuilder<>(ApiResponse.success(data));
    }

    // ==================== 静态工厂方法：RESTful 语义成功响应 ====================

    /**
     * 构建一个 HTTP 201 Created 响应，携带创建的资源数据。
     *
     * @param data 创建的资源数据
     * @param <T>  数据类型
     * @return ResponseEntity 包裹的 ApiResponse，状态码 201 CREATED
     */
    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        ApiResponse<T> response = ApiResponse.success(data);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 构建一个 HTTP 201 Created 响应，并设置 Location 头。
     *
     * @param data     创建的资源数据
     * @param location 资源位置 URI
     * @param <T>      数据类型
     * @return ResponseEntity 包裹的 ApiResponse，状态码 201 CREATED，带 Location 头
     */
    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String location) {
        ApiResponse<T> response = ApiResponse.success(data);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, location)
                .body(response);
    }

    /**
     * 构建一个 HTTP 201 Created 响应的构建器（便于添加 Header）
     *
     * @param data 创建的资源数据
     * @param <T>  数据类型
     * @return ResponseBuilder 实例
     */
    public static <T> ResponseBuilder<T> createdBuilder(T data) {
        return new ResponseBuilder<>(ApiResponse.success(data))
                .status(HttpStatus.CREATED);
    }

    /**
     * 构建一个 HTTP 204 No Content 响应，无响应体。
     *
     * @param <T> 数据类型（通常为 Void）
     * @return ResponseEntity，状态码 204 NO_CONTENT，无响应体
     */
    public static <T> ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    /**
     * 构建一个 HTTP 202 Accepted 响应，用于异步任务提交成功。
     *
     * @param message 提示信息
     * @param <T>     数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse，状态码 202 ACCEPTED
     */
    public static <T> ResponseEntity<ApiResponse<T>> accepted(String message) {
        ApiResponse<T> response = ApiResponse.success(message, null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ==================== 静态工厂方法：业务失败（HTTP 200 + 业务错误码） ====================

    /**
     * 构建一个 HTTP 200 的业务失败响应，使用 ErrorCode 中的默认消息。
     * <p>
     * 适用场景：业务校验失败、余额不足、用户不存在等，HTTP 状态码仍为 200。
     * <p>
     * HTTP 200 表示请求已正常处理，业务成功/失败由 ApiResponse.code 区分。
     *
     * @param errorCode 错误码枚举
     * @param <T>       数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse，状态码 200 OK，但业务状态码为错误码
     */
    public static <T> ResponseEntity<ApiResponse<T>> fail(ErrorCode errorCode) {
        return ResponseEntity.ok(ApiResponse.error(errorCode));
    }

    /**
     * 构建一个 HTTP 200 的业务失败响应，使用自定义消息覆盖 ErrorCode 中的默认消息。
     *
     * @param errorCode     错误码枚举
     * @param customMessage 自定义错误描述
     * @param <T>           数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse，状态码 200 OK
     */
    public static <T> ResponseEntity<ApiResponse<T>> fail(ErrorCode errorCode, String customMessage) {
        return ResponseEntity.ok(ApiResponse.error(errorCode, customMessage));
    }

    /**
     * 构建一个 HTTP 200 的业务失败响应的构建器（便于添加 Header）
     *
     * @param errorCode 错误码枚举
     * @param <T>       数据类型（通常为 Void）
     * @return ResponseBuilder 实例
     */
    public static <T> ResponseBuilder<T> failBuilder(ErrorCode errorCode) {
        return new ResponseBuilder<>(ApiResponse.error(errorCode));
    }

    // ==================== 静态工厂方法：HTTP 协议错误（非 200 状态码） ====================

    /**
     * 构建一个 HTTP 协议错误响应，使用 ErrorCode 中的默认消息。
     * <p>
     * 适用场景：资源不存在（404）、无权限（403）、未认证（401）等 HTTP 协议层错误。
     *
     * @param status    HTTP 状态码
     * @param errorCode 错误码枚举
     * @param <T>       数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, ErrorCode errorCode) {
        ApiResponse<T> response = ApiResponse.error(errorCode);
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 构建一个 HTTP 协议错误响应，使用自定义消息。
     *
     * @param status    HTTP 状态码
     * @param errorCode 错误码枚举
     * @param message   自定义错误描述
     * @param <T>       数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, ErrorCode errorCode, String message) {
        ApiResponse<T> response = ApiResponse.error(errorCode, message);
        return ResponseEntity.status(status).body(response);
    }

    // ==================== 错误响应（无数据，最常用） ====================

    /**
     * 构建一个 HTTP 协议错误响应的构建器（无数据，默认 Void）
     */
    public static ResponseBuilder<Void> errorBuilder(HttpStatus status, ErrorCode errorCode) {
        return new ResponseBuilder<>(ApiResponse.<Void>error(errorCode))
                .status(status);
    }

    // ==================== 错误响应（携带数据，泛型版本） ====================

    /**
     * 构建一个 HTTP 协议错误响应的构建器（携带业务数据）
     *
     * @param status    HTTP 状态码
     * @param errorCode 错误码枚举
     * @param data      业务数据
     * @param <T>       数据类型
     * @return ResponseBuilder 实例
     */
    public static <T> ResponseBuilder<T> errorBuilder(HttpStatus status, ErrorCode errorCode, T data) {
        return new ResponseBuilder<>(ApiResponse.of(errorCode.getCode(), errorCode.getMessage(), data))
                .status(status);
    }

    /**
     * 构建一个 HTTP 404 Not Found 响应。
     *
     * @param errorCode 错误码枚举（如 USER_NOT_FOUND）
     * @param <T>       数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse，状态码 404 NOT_FOUND
     */
    public static <T> ResponseEntity<ApiResponse<T>> notFound(ErrorCode errorCode) {
        return error(HttpStatus.NOT_FOUND, errorCode);
    }

    /**
     * 构建一个 HTTP 404 Not Found 响应，自定义消息。
     *
     * @param errorCode 错误码枚举
     * @param message   自定义消息
     * @param <T>       数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse，状态码 404 NOT_FOUND
     */
    public static <T> ResponseEntity<ApiResponse<T>> notFound(ErrorCode errorCode, String message) {
        return error(HttpStatus.NOT_FOUND, errorCode, message);
    }

    /**
     * 构建一个 HTTP 403 Forbidden 响应（无权限）。
     *
     * @param errorCode 错误码枚举（如 AUTH_FORBIDDEN）
     * @param <T>       数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse，状态码 403 FORBIDDEN
     */
    public static <T> ResponseEntity<ApiResponse<T>> forbidden(ErrorCode errorCode) {
        return error(HttpStatus.FORBIDDEN, errorCode);
    }

    /**
     * 构建一个 HTTP 401 Unauthorized 响应（未认证）。
     *
     * @param errorCode 错误码枚举（如 AUTH_UNAUTHORIZED）
     * @param <T>       数据类型（通常为 Void）
     * @return ResponseEntity 包裹的 ApiResponse，状态码 401 UNAUTHORIZED
     */
    public static <T> ResponseEntity<ApiResponse<T>> unauthorized(ErrorCode errorCode) {
        return error(HttpStatus.UNAUTHORIZED, errorCode);
    }

    // ==================== 静态工厂方法：完全自定义 ====================

    /**
     * 完全自定义响应（HTTP 状态码与业务状态码一致）。
     *
     * @param status  HTTP 状态枚举
     * @param message 提示信息
     * @param data    业务数据
     * @param <T>     数据类型
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> of(HttpStatus status, String message, T data) {
        ApiResponse<T> response = ApiResponse.of(status.value(), message, data);
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 完全自定义响应（HTTP 状态码与业务状态码解耦）。
     *
     * @param httpStatus   HTTP 状态枚举
     * @param businessCode 业务状态码
     * @param message      提示信息
     * @param data         业务数据
     * @param <T>          数据类型
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> custom(HttpStatus httpStatus,
                                                            int businessCode,
                                                            String message,
                                                            T data) {
        ApiResponse<T> response = ApiResponse.of(businessCode, message, data);
        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * 完全自定义响应（HTTP 状态码与业务状态码解耦，使用 ErrorCode）。
     *
     * @param httpStatus HTTP 状态枚举
     * @param errorCode  业务错误码枚举
     * @param data       业务数据
     * @param <T>        数据类型
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> custom(HttpStatus httpStatus,
                                                            ErrorCode errorCode,
                                                            T data) {
        ApiResponse<T> response = ApiResponse.of(errorCode.getCode(), errorCode.getMessage(), data);
        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * 完全自定义响应（HTTP 状态码与业务状态码解耦，使用 ErrorCode + 自定义消息）。
     *
     * @param httpStatus HTTP 状态枚举
     * @param errorCode  业务错误码枚举
     * @param message    自定义消息
     * @param data       业务数据
     * @param <T>        数据类型
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> custom(HttpStatus httpStatus,
                                                            ErrorCode errorCode,
                                                            String message,
                                                            T data) {
        ApiResponse<T> response = ApiResponse.of(errorCode.getCode(), message, data);
        return ResponseEntity.status(httpStatus).body(response);
    }

    // ==================== 分页响应 ====================

    /**
     * 构建一个 HTTP 200 的分页成功响应。
     * <p>
     * 建议配合 {@link PageResponse} 使用，或直接传入 Page 对象。
     *
     * @param pageData 分页数据（如 PageResponse 或 Page 对象）
     * @param <T>      数据类型
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> page(T pageData) {
        return ResponseEntity.ok(ApiResponse.success(pageData));
    }

    /**
     * 构建一个 HTTP 200 的分页成功响应，自定义消息。
     *
     * @param message  自定义消息
     * @param pageData 分页数据
     * @param <T>      数据类型
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> page(String message, T pageData) {
        return ResponseEntity.ok(ApiResponse.success(message, pageData));
    }

    /**
     * 构建一个 HTTP 200 的分页成功响应的构建器（便于添加 Header）
     *
     * @param pageData 分页数据
     * @param <T>      数据类型
     * @return ResponseBuilder 实例
     */
    public static <T> ResponseBuilder<T> pageBuilder(T pageData) {
        return new ResponseBuilder<>(ApiResponse.success(pageData));
    }

    // ==================== 带 Header 的便捷方法（保留原有接口兼容性） ====================

    /**
     * 在已有 ApiResponse 基础上添加 HTTP 头，并返回 HTTP 200 OK。
     *
     * @param response 已构建的 ApiResponse 对象
     * @param headers  Spring HttpHeaders 对象
     * @param <T>      数据类型
     * @return ResponseEntity 包裹的 ApiResponse，状态码 200 OK
     */
    public static <T> ResponseEntity<ApiResponse<T>> withHeaders(ApiResponse<T> response, HttpHeaders headers) {
        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * 在已有 ApiResponse 基础上添加自定义键值对形式的 HTTP 头，并返回 HTTP 200 OK。
     *
     * @param response   已构建的 ApiResponse 对象
     * @param headersMap 头信息的键值对
     * @param <T>        数据类型
     * @return ResponseEntity 包裹的 ApiResponse，状态码 200 OK
     */
    public static <T> ResponseEntity<ApiResponse<T>> withHeaders(ApiResponse<T> response,
                                                                 Map<String, String> headersMap) {
        HttpHeaders headers = new HttpHeaders();
        if (headersMap != null) {
            headersMap.forEach((key, value) -> {
                if (value != null) {
                    headers.add(key, value);
                }
            });
        }
        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * 在已有 ApiResponse 基础上添加 HTTP 头，支持自定义 HTTP 状态码。
     *
     * @param response 已构建的 ApiResponse 对象
     * @param status   HTTP 状态码
     * @param headers  头信息
     * @param <T>      数据类型
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> withHeaders(ApiResponse<T> response,
                                                                 HttpStatus status,
                                                                 HttpHeaders headers) {
        return ResponseEntity.status(status).headers(headers).body(response);
    }

    // ==================== 通用转换辅助方法 ====================

    /**
     * 将任意对象转换为成功的 ApiResponse 响应。
     * <p>
     * 适用于需要将已有数据包装为响应的场景，配合 {@link #success(Object)} 使用。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @param <R>  返回类型（通常为 ResponseEntity）
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <T, R> ResponseEntity<ApiResponse<T>> wrap(T data) {
        return success(data);
    }

    /**
     * 将任意对象通过转换函数转换为成功的 ApiResponse 响应。
     * <p>
     * 使用示例：
     * <pre>
     * return ResponseBuilder.wrap(user, UserVO::from);
     * </pre>
     *
     * @param source 源数据
     * @param mapper 转换函数
     * @param <S>    源数据类型
     * @param <T>    目标数据类型
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <S, T> ResponseEntity<ApiResponse<T>> wrap(S source, Function<S, T> mapper) {
        return success(mapper.apply(source));
    }

    /**
     * 将任意对象通过转换函数转换为分页响应。
     *
     * @param source 源数据
     * @param mapper 转换函数
     * @param <S>    源数据类型
     * @param <T>    目标数据类型
     * @return ResponseEntity 包裹的 ApiResponse
     */
    public static <S, T> ResponseEntity<ApiResponse<T>> wrapPage(S source, Function<S, T> mapper) {
        return page(mapper.apply(source));
    }

    // ==================== 私有构造，防止实例化 ====================

    private ResponseBuilder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
