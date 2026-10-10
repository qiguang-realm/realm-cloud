package cn.realm.cloud.framework.common.exception;

import cn.realm.cloud.framework.common.enums.ErrorCode;

import java.io.Serial;
import java.util.Objects;

/**
 * 业务异常。
 *
 * <p><b>语义定位：</b>表示<b>业务规则违反</b>——请求已正常处理，但业务上不允许。
 * 典型场景：
 * <ul>
 *     <li>用户不存在、用户已禁用</li>
 *     <li>订单不存在、订单状态不允许此操作</li>
 *     <li>账户余额不足</li>
 *     <li>业务校验失败（如"手机号已被注册"）</li>
 * </ul>
 *
 * <p><b>与 {@link SystemException} 的区别：</b>
 * <table border="1">
 *     <caption>异常语义对比</caption>
 *     <tr><th>维度</th><th>BusinessException</th><th>SystemException</th></tr>
 *     <tr><td>语义</td><td>业务规则违反</td><td>系统级失败</td></tr>
 *     <tr><td>可预期性</td><td>✅ 可预期（用户操作导致）</td><td>❌ 不可预期（系统问题）</td></tr>
 *     <tr><td>HTTP 状态</td><td>200</td><td>5xx</td></tr>
 *     <tr><td>日志级别</td><td>WARN</td><td>ERROR（触发告警）</td></tr>
 *     <tr><td>业务 code 段位</td><td>2xxx</td><td>1xxx / 4xxx</td></tr>
 *     <tr><td>用户可见消息</td><td>✅ 展示 {@code e.getMessage()}</td><td>⚠️ 仅展示"系统繁忙"</td></tr>
 *     <tr><td>是否重试</td><td>否</td><td>视情况（如超时）</td></tr>
 * </table>
 *
 * <p><b>设计定位：</b>继承 {@link RuntimeException}——业务异常是<b>流程控制</b>，
 * 由 {@code GlobalExceptionHandler} 统一捕获并转换为 {@code ApiResponse}，
 * 业务代码<b>不应</b> try-catch（Effective Java Item 70）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 使用 ErrorCode 默认消息
 * throw BusinessException.of(ErrorCode.USER_NOT_FOUND);
 *
 * // 2. 自定义消息覆盖（携带动态参数）
 * throw BusinessException.of(ErrorCode.USER_NOT_FOUND, "用户 ID " + id + " 不存在");
 *
 * // 3. 包装根因
 * try {
 *     remoteService.call();
 * } catch (IOException e) {
 *     throw BusinessException.of(ErrorCode.REMOTE_SERVICE_ERROR, e);
 * }
 * }</pre>
 *
 * @author QI Guang
 * @see SystemException
 * @see ErrorCode
 * @since 1.0.0
 */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码（不可变）。
     *
     * <p>通常使用 2xxx（业务级）区段的错误码。
     */
    private final ErrorCode errorCode;

    // ==================== 构造器（私有，强制使用静态工厂） ====================

    private BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode 不能为 null").getMessage());
        this.errorCode = errorCode;
    }

    private BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null ? customMessage
                : Objects.requireNonNull(errorCode, "errorCode 不能为 null").getMessage());
        this.errorCode = errorCode;
    }

    private BusinessException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode 不能为 null").getMessage(), cause);
        this.errorCode = errorCode;
    }

    private BusinessException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage != null ? customMessage
                : Objects.requireNonNull(errorCode, "errorCode 不能为 null").getMessage(), cause);
        this.errorCode = errorCode;
    }

    // ==================== 静态工厂 ====================

    /**
     * 使用 {@link ErrorCode} 默认消息创建。
     *
     * @param errorCode 错误码，不允许为 {@code null}
     * @return BusinessException 实例
     */
    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    /**
     * 使用自定义消息覆盖 {@link ErrorCode} 默认消息创建。
     *
     * <p><b>使用场景：</b>需要在错误消息中携带动态参数（如订单号、用户 ID）。
     *
     * @param errorCode     错误码，不允许为 {@code null}
     * @param customMessage 自定义消息；为 {@code null} 时使用 {@link ErrorCode#getMessage()}
     * @return BusinessException 实例
     */
    public static BusinessException of(ErrorCode errorCode, String customMessage) {
        return new BusinessException(errorCode, customMessage);
    }

    /**
     * 使用 {@link ErrorCode} 默认消息 + 根因创建。
     *
     * <p><b>使用场景：</b>包装底层异常（IO / RPC / 数据库）。
     *
     * @param errorCode 错误码，不允许为 {@code null}
     * @param cause     根因，允许为 {@code null}
     * @return BusinessException 实例
     */
    public static BusinessException of(ErrorCode errorCode, Throwable cause) {
        return new BusinessException(errorCode, cause);
    }

    /**
     * 使用自定义消息 + 根因创建。
     *
     * @param errorCode     错误码，不允许为 {@code null}
     * @param customMessage 自定义消息；为 {@code null} 时使用 {@link ErrorCode#getMessage()}
     * @param cause         根因，允许为 {@code null}
     * @return BusinessException 实例
     */
    public static BusinessException of(ErrorCode errorCode, String customMessage, Throwable cause) {
        return new BusinessException(errorCode, customMessage, cause);
    }

    // ==================== 访问器 ====================

    /**
     * 获取业务错误码。
     *
     * @return 错误码，永不为 {@code null}
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取业务错误码的数字值（便捷方法）。
     *
     * @return 错误码数字值
     */
    public int getCode() {
        return errorCode.getCode();
    }
}
