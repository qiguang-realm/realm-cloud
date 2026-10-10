package cn.realm.cloud.framework.common.base.api;

import cn.realm.cloud.framework.common.enums.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 统一 API 响应体。
 *
 * <p><b>设计目标：</b>为所有 REST API 提供一致的响应结构，
 * 让前端可以统一拦截和处理成功 / 失败响应。
 *
 * <p><b>字段说明：</b>
 * <ul>
 *     <li>{@link #code}：业务状态码，取自 {@link ErrorCode#getCode()}；0 表示成功</li>
 *     <li>{@link #message}：面向用户或开发者的提示信息</li>
 *     <li>{@link #data}：业务数据，可为 {@code null}</li>
 *     <li>{@link #timestamp}：响应时间戳（epoch 毫秒），UTC 无时区歧义</li>
 *     <li>{@link #traceId}：全链路追踪 ID，用于分布式链路诊断</li>
 * </ul>
 *
 * <p><b>不可变性：</b>本类为<b>只读 DTO</b>，所有字段由静态工厂设置后不可修改。
 * 唯一例外是 {@link #withTraceId(String)}，它返回<b>新的不可变副本</b>。
 *
 * <p><b>时区处理：</b>{@link #timestamp} 使用 {@code long}（epoch 毫秒），
 * 不携带时区信息，前端可通过 {@code new Date(timestamp)} 自动按本地时区展示。
 * 若需 ISO 8601 格式（如 {@code 2026-10-10T04:00:00.000Z}），
 * 请在 {@code application.yml} 中全局配置：
 * <pre>
 * spring:
 *   jackson:
 *     serialization:
 *       write-dates-as-timestamps: false
 *     date-format: yyyy-MM-dd'T'HH:mm:ss.SSSXXX
 *     time-zone: UTC
 * </pre>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 成功响应
 * return ApiResponse.success(user);
 *
 * // 错误响应
 * return ApiResponse.error(ErrorCode.USER_NOT_FOUND);
 *
 * // 带 traceId
 * return ApiResponse.success(data).withTraceId(traceId);
 *
 * // 配合 ResponseEntity 使用
 * return ApiResponse.success(user).toResponseEntity();
 * }</pre>
 *
 * @param <T> 业务数据类型
 * @author QI Guang
 * @since 1.0.0
 */
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码（0 表示成功）。
     */
    private int code;

    /**
     * 提示信息。
     */
    private String message;

    /**
     * 业务数据。
     */
    private T data;

    /**
     * 响应时间戳（epoch 毫秒，UTC）。
     */
    private long timestamp;

    /**
     * 全链路追踪 ID。
     */
    private String traceId;

    // ---------- 私有构造器 ----------

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
    }

    // ---------- 链式方法（不可变） ----------

    /**
     * 返回携带 {@code traceId} 的<b>新响应对象</b>，原对象不受影响。
     *
     * @param traceId 全链路追踪 ID
     * @return 新的 {@link ApiResponse} 实例
     */
    public ApiResponse<T> withTraceId(String traceId) {
        ApiResponse<T> copy = new ApiResponse<>(this.code, this.message, this.data);
        copy.timestamp = this.timestamp;  // 保留原时间戳
        copy.traceId = traceId;
        return copy;
    }

    // ---------- 静态工厂：成功响应 ----------

    /**
     * 无数据的成功响应（{@code code=0, message="操作成功"}）。
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(), null);
    }

    /**
     * 带数据的成功响应。
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(), data);
    }

    /**
     * 带自定义消息和数据的成功响应。
     *
     * @param message 自定义成功消息（覆盖默认"操作成功"）
     * @param data    业务数据
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    // ---------- 静态工厂：错误响应 ----------

    /**
     * 使用预定义 {@link ErrorCode} 构造错误响应（无数据）。
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 使用预定义 {@link ErrorCode} + 自定义消息构造错误响应。
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String customMessage) {
        return new ApiResponse<>(errorCode.getCode(), customMessage, null);
    }

    /**
     * 直接使用数字状态码和消息构造错误响应。
     *
     * @deprecated 建议使用 {@link #error(ErrorCode)} 或 {@link #error(ErrorCode, String)}，
     * 避免业务状态码分散在代码各处，难以维护。
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // ---------- 完全自定义 ----------

    /**
     * 完全自定义响应（允许任意 code、message 和 data）。
     *
     * <p>适用于需要非标准业务状态码的场景。优先使用 {@link #success} / {@link #error}。
     */
    public static <T> ApiResponse<T> of(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    // ---------- 实例方法 ----------

    /**
     * 判断当前响应是否成功。
     *
     * @return {@code true} 表示业务处理成功（{@code code == 0}）
     */
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }

    /**
     * 包装为 Spring {@link org.springframework.http.ResponseEntity}，HTTP 状态固定 200 OK。
     *
     * <p>推荐使用此方式：业务状态码完全承载业务结果，便于前端统一拦截。
     */
    public org.springframework.http.ResponseEntity<ApiResponse<T>> toResponseEntity() {
        return org.springframework.http.ResponseEntity.ok(this);
    }

    /**
     * 包装为指定 HTTP 状态的 {@link org.springframework.http.ResponseEntity}。
     *
     * <p>仅在需要利用 HTTP 协议本身的语义（如 404、500）时使用。
     */
    public org.springframework.http.ResponseEntity<ApiResponse<T>> toResponseEntity(
            org.springframework.http.HttpStatus status) {
        return org.springframework.http.ResponseEntity.status(status).body(this);
    }
}
