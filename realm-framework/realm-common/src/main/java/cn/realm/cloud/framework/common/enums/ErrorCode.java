package cn.realm.cloud.framework.common.enums;

/**
 * 错误码枚举代码
 *
 * @author QI Guang
 */
public enum ErrorCode {

    SUCCESS(0, "操作成功"),

    // ==================== 系统级 (1xxx) ====================
    SYSTEM_ERROR(1000, "系统繁忙，请稍后再试"),
    PARAM_INVALID(1001, "请求参数无效或格式错误"),
    MISSING_PARAM(1002, "缺少必要的请求参数"),
    MEDIA_TYPE_UNSUPPORTED(1003, "不支持的媒体类型"),
    METHOD_NOT_ALLOWED(1004, "不支持的HTTP方法"),

    // ==================== 业务级 (2xxx) ====================
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_DISABLED(2002, "用户已被禁用"),
    ORDER_NOT_FOUND(2101, "订单不存在"),
    ORDER_STATUS_INVALID(2102, "订单状态不允许此操作"),
    INSUFFICIENT_BALANCE(2201, "账户余额不足"),

    // ==================== 认证授权级 (3xxx) ====================
    AUTH_UNAUTHORIZED(3001, "未登录或Token已过期"),
    AUTH_FORBIDDEN(3002, "无权限访问该资源"),
    AUTH_TOKEN_INVALID(3003, "Token无效或篡改"),

    // ==================== 第三方/依赖级 (4xxx) ====================
    REMOTE_SERVICE_ERROR(4001, "依赖的第三方服务异常"),
    REMOTE_TIMEOUT(4002, "依赖服务调用超时");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return null;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}
