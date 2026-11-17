package cn.realm.cloud.framework.core.exception;

import lombok.Getter;

import java.util.*;

/**
 * 框架级通用错误码。
 *
 * <p><b>定位：</b>实现 {@link ErrorCode}，覆盖跨模块通用的错误码——
 * 框架级故障、协议层错误、认证授权、依赖调用、数据访问、文件存储、
 * 消息调度、多租户配置、兜底。不包含 2xxx 业务码。
 *
 * <p><b>HTTP 映射：</b>本枚举<b>不携带 HTTP 状态</b>。HTTP 映射由
 * {@code realm-web} 的 {@code ErrorCodeHttpStatusMapping} 维护，
 * 未映射时兜底 500。这遵循 Google `Status` 模型——错误码是逻辑标识，
 * HTTP 是传输层适配。
 *
 * <p><b>分段规划：</b>
 * <ul>
 *     <li>1xxx：框架级通用（系统故障 + 协议层错误）</li>
 *     <li>3xxx：认证授权级</li>
 *     <li>4xxx：依赖级（RPC / MQ / 缓存）</li>
 *     <li>5xxx：数据访问级</li>
 *     <li>6xxx：文件 / 对象存储级</li>
 *     <li>7xxx：消息 / 调度级</li>
 *     <li>8xxx：多租户 / 配置 / 特性环境级</li>
 *     <li>9xxx：兜底</li>
 * </ul>
 * 2xxx 段位预留给业务模块，框架层不声明。
 *
 * @author QI Guang
 * @see ErrorCode
 */
@Getter
public enum FrameworkErrorCode implements ErrorCode {

    // ==================== 1xxx 框架级 ====================
    SYSTEM_ERROR(1000, "系统繁忙，请稍后再试"),
    PARAM_INVALID(1001, "请求参数无效或格式错误"),
    MISSING_PARAM(1002, "缺少必要的请求参数"),
    MEDIA_TYPE_UNSUPPORTED(1003, "不支持的媒体类型"),
    METHOD_NOT_ALLOWED(1004, "不支持的 HTTP 方法"),
    NOT_FOUND(1005, "请求的资源不存在"),
    REQUEST_TIMEOUT(1006, "请求超时，请稍后重试"),
    RATE_LIMITED(1007, "请求过于频繁，请稍后再试"),
    CONFLICT(1008, "资源状态冲突"),
    UNPROCESSABLE_ENTITY(1009, "请求语义无法处理"),
    IDEMPOTENCY_CONFLICT(1010, "重复请求，幂等键冲突"),
    OPTIMISTIC_LOCK_CONFLICT(1011, "数据已被修改，请刷新后重试"),
    DATA_INTEGRITY_VIOLATION(1012, "数据完整性约束违反"),

    // ==================== 3xxx 认证授权 ====================
    AUTH_UNAUTHORIZED(3001, "未登录或 Token 已过期"),
    AUTH_FORBIDDEN(3002, "无权限访问该资源"),
    AUTH_TOKEN_INVALID(3003, "Token 无效或已被篡改"),
    AUTH_TOKEN_EXPIRED(3004, "Token 已过期"),
    AUTH_TOKEN_REVOKED(3005, "Token 已被撤销"),
    AUTH_INSUFFICIENT_SCOPE(3006, "Token 权限范围不足"),
    AUTH_MFA_REQUIRED(3007, "需要多因素认证"),
    AUTH_ACCOUNT_LOCKED(3008, "账户已被锁定"),
    AUTH_CREDENTIALS_EXPIRED(3009, "凭证已过期"),

    // ==================== 4xxx 依赖 ====================
    REMOTE_SERVICE_ERROR(4001, "依赖的第三方服务异常"),
    REMOTE_TIMEOUT(4002, "依赖服务调用超时"),
    REMOTE_BAD_GATEWAY(4003, "依赖服务返回无效响应"),
    REMOTE_CIRCUIT_OPEN(4004, "依赖服务熔断器已打开"),
    REMOTE_RATE_LIMITED(4005, "依赖服务限流"),

    // ==================== 5xxx 数据访问 ====================
    DATA_ACCESS_ERROR(5001, "数据访问异常"),
    DATA_NOT_FOUND(5002, "数据不存在"),
    DATA_DUPLICATE(5003, "数据重复"),
    DATA_OPTIMISTIC_LOCK_CONFLICT(5004, "数据并发修改冲突"),
    DATA_TRANSACTION_CONFLICT(5005, "事务冲突"),
    DATA_CONNECTION_EXHAUSTED(5006, "数据库连接池耗尽"),
    DATA_MIGRATION_FAILED(5007, "数据迁移失败"),

    // ==================== 6xxx 文件 / 对象存储 ====================
    FILE_NOT_FOUND(6001, "文件不存在"),
    FILE_TYPE_UNSUPPORTED(6002, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(6003, "文件大小超出限制"),
    FILE_UPLOAD_FAILED(6004, "文件上传失败"),
    FILE_PARSE_FAILED(6005, "文件解析失败"),
    FILE_STORAGE_UNAVAILABLE(6006, "对象存储不可用"),

    // ==================== 7xxx 消息 / 调度 ====================
    MESSAGE_PUBLISH_FAILED(7001, "消息发布失败"),
    MESSAGE_CONSUME_FAILED(7002, "消息消费失败"),
    MESSAGE_DUPLICATE(7003, "重复消息"),
    SCHEDULING_JOB_NOT_FOUND(7004, "调度任务不存在"),
    SCHEDULING_JOB_EXECUTION_FAILED(7005, "调度任务执行失败"),

    // ==================== 8xxx 多租户 / 配置 ====================
    TENANT_NOT_FOUND(8001, "租户不存在"),
    TENANT_CONTEXT_MISSING(8002, "租户上下文缺失"),
    TENANT_DISABLED(8003, "租户已被禁用"),
    CONFIG_NOT_FOUND(8004, "配置项不存在"),
    CONFIG_REFRESH_FAILED(8005, "配置刷新失败"),
    FEATURE_ENV_NOT_FOUND(8006, "特性环境不存在"),

    // ==================== 9xxx 兜底 ====================
    UNKNOWN_ERROR(9999, "未知错误");

    private static final Map<Integer, FrameworkErrorCode> CODE_MAP;

    static {
        Map<Integer, FrameworkErrorCode> map = new HashMap<>();
        for (FrameworkErrorCode errorCode : values()) {
            FrameworkErrorCode existing = map.putIfAbsent(errorCode.code, errorCode);
            if (existing != null) {
                throw new IllegalStateException(String.format(
                        "FrameworkErrorCode 重复定义：code=%d 同时归属 %s 和 %s",
                        errorCode.code, existing.name(), errorCode.name()));
            }
        }
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    private final int code;
    private final String message;

    FrameworkErrorCode(int code, String message) {
        this.code = code;
        this.message = Objects.requireNonNull(message, "message 不能为 null：" + name());
    }

    public static Optional<FrameworkErrorCode> fromCode(int code) {
        return Optional.ofNullable(CODE_MAP.get(code));
    }

    public static FrameworkErrorCode fromCodeOrDefault(int code) {
        return CODE_MAP.getOrDefault(code, UNKNOWN_ERROR);
    }

    public boolean isSuccess() {
        return this.code == 0;
    }

    public boolean isFailure() {
        return !isSuccess();
    }
}
