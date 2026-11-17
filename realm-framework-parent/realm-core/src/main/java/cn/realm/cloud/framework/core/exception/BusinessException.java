package cn.realm.cloud.framework.core.exception;

import java.io.Serial;
import java.util.Objects;

/**
 * 业务异常。
 *
 * <p><b>语义定位：</b>表示业务规则违反——请求已正常处理，但业务上不允许。
 *
 * <p><b>HTTP 状态映射：</b>由 {@code realm-web} 的
 * {@code ErrorCodeHttpStatusMapping} 负责。本类不关心 HTTP，只承载
 * {@link ErrorCode}。业务代码无需关心 HTTP 状态，只需选择正确的
 * {@link ErrorCode}。
 *
 * <p><b>{@link ErrorCode} 实现的可序列化性：</b>异常跨进程传递时，
 * {@link ErrorCode} 的实现必须可序列化。**推荐所有实现使用枚举**——
 * 枚举天然可序列化且能保持单例语义。
 *
 * @author QI Guang
 * @see SystemException
 * @see ErrorCode
 * @see FrameworkErrorCode
 */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    private BusinessException(ErrorCode errorCode) {
        super(resolveMessage(errorCode, null));
        this.errorCode = errorCode;
    }

    private BusinessException(ErrorCode errorCode, String customMessage) {
        super(resolveMessage(errorCode, customMessage));
        this.errorCode = errorCode;
    }

    private BusinessException(ErrorCode errorCode, Throwable cause) {
        super(resolveMessage(errorCode, null), cause);
        this.errorCode = errorCode;
    }

    private BusinessException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(resolveMessage(errorCode, customMessage), cause);
        this.errorCode = errorCode;
    }

    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException of(ErrorCode errorCode, String customMessage) {
        return new BusinessException(errorCode, customMessage);
    }

    public static BusinessException of(ErrorCode errorCode, Throwable cause) {
        return new BusinessException(errorCode, cause);
    }

    public static BusinessException of(ErrorCode errorCode, String customMessage, Throwable cause) {
        return new BusinessException(errorCode, customMessage, cause);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    private static String resolveMessage(ErrorCode errorCode, String customMessage) {
        Objects.requireNonNull(errorCode, "errorCode 不能为 null");
        if (customMessage != null) {
            return customMessage;
        }
        return Objects.requireNonNull(errorCode.getMessage(),
                "errorCode.getMessage() 不能为 null："
                        + errorCode.getClass().getName() + "." + errorCode);
    }
}
