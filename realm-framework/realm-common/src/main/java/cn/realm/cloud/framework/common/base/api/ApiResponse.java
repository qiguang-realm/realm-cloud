package cn.realm.cloud.framework.common.base.api;

import cn.realm.cloud.framework.common.enums.ErrorCode;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.Serializable;
import java.time.Instant;

/**
 * 统一 API 响应体
 *
 * @author QI Guang
 */
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 供序列化框架使用
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码（取自 {@link ErrorCode#getCode()}）
     */
    private int code;

    /**
     * 提示信息（面向用户或开发者的描述）
     */
    private String message;

    /**
     * 业务数据（可为 null）
     */
    private T data;

    /**
     * 响应时间戳（毫秒），由构造器自动生成，禁止外部修改
     */
    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
//    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Instant timestamp;

    /**
     * 全链路追踪 ID（分布式环境下的唯一标识）
     */
    private String traceId;

    // ---------- 私有构造器（强制使用静态工厂） ----------

    /**
     * 全参构造器（私有），用于创建响应对象并自动填充时间戳。
     *
     * @param code    业务状态码
     * @param message 提示信息
     * @param data    业务数据
     */
    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = (message != null) ? message : "";
        this.data = data;
        this.timestamp = Instant.now();
        // traceId 默认为 null，可通过 withTraceId() 链式设置
    }

    // ---------- 链式方法 ----------

    /**
     * 链式设置 TraceId，用于全局拦截器或日志上下文中注入。
     *
     * @param traceId 全链路追踪 ID
     * @return 当前响应对象
     */
    public ApiResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    // ---------- 静态工厂方法（成功响应） ----------

    /**
     * 构造一个无数据的成功响应（code=0, message="操作成功"）
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构造一个带数据的成功响应（code=0, message="操作成功"）
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构造一个带自定义消息和数据的成功响应（code=0）
     *
     * @param message 自定义成功消息（会覆盖默认的"操作成功"）
     * @param data    业务数据
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    // ---------- 静态工厂方法（错误响应） ----------

    /**
     * 使用预定义的 {@link ErrorCode} 构造错误响应（无数据）
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 使用预定义的 {@link ErrorCode} + 自定义错误消息构造错误响应（无数据）
     *
     * @param errorCode     错误码枚举
     * @param customMessage 自定义错误描述（覆盖枚举中的默认消息）
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String customMessage) {
        return new ApiResponse<>(errorCode.getCode(), customMessage, null);
    }

    /**
     * 直接使用数字状态码和消息构造错误响应（兜底方案，不推荐直接使用，建议使用枚举）
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // ---------- 完全自定义响应 ----------

    /**
     * 完全自定义响应（允许任意 code、message 和 data）
     * <p>适用于需要非标准业务状态码或特殊消息的场景。
     */
    public static <T> ApiResponse<T> of(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    // ---------- 实例方法 ----------

    /**
     * 判断当前响应是否成功（动态比对 {@link ErrorCode#SUCCESS} 的 code，消除魔法值）
     *
     * @return true 表示业务处理成功
     */
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }

    /**
     * 将当前响应包装为 Spring 的 {@link ResponseEntity}，HTTP 状态固定为 200 OK。
     * <p>推荐使用此方式，让业务状态码完全承载业务结果，便于前端统一拦截。
     */
    public ResponseEntity<ApiResponse<T>> toResponseEntity() {
        return ResponseEntity.ok(this);
    }

    /**
     * 将当前响应包装为指定 HTTP 状态的 {@link ResponseEntity}。
     * <p>仅在需要利用 HTTP 协议本身的语义（如 404, 500）时使用。
     *
     * @param status HTTP 状态枚举
     */
    public ResponseEntity<ApiResponse<T>> toResponseEntity(HttpStatus status) {
        return ResponseEntity.status(status).body(this);
    }
}
