package cn.realm.cloud.framework.common.exception;

import cn.realm.cloud.framework.common.enums.ErrorCode;

import java.io.Serial;
import java.util.Objects;

/**
 * 系统异常。
 *
 * <p><b>语义定位：</b>表示<b>系统级失败</b>——非业务规则违反，而是技术层面的失败。
 * 典型场景：
 * <ul>
 *     <li>依赖服务不可用（RPC / MQ / Cache 调用失败）</li>
 *     <li>依赖服务调用超时</li>
 *     <li>数据库连接池耗尽、SQL 执行异常</li>
 *     <li>数据一致性校验失败（如乐观锁冲突）</li>
 *     <li>配置错误、资源耗尽</li>
 *     <li>未分类的内部错误（兜底）</li>
 * </ul>
 *
 * <p><b>与 {@link BusinessException} 的区别：</b>
 * <table border="1">
 *     <caption>异常语义对比</caption>
 *     <tr><th>维度</th><th>BusinessException</th><th>SystemException</th></tr>
 *     <tr><td>语义</td><td>业务规则违反</td><td>系统级失败</td></tr>
 *     <tr><td>可预期性</td><td>✅ 可预期（用户操作导致）</td><td>❌ 不可预期（系统问题）</td></tr>
 *     <tr><td>HTTP 状态</td><td>200</td><td>5xx（500 / 503）</td></tr>
 *     <tr><td>日志级别</td><td>WARN</td><td>ERROR（触发告警）</td></tr>
 *     <tr><td>业务 code 段位</td><td>2xxx</td><td>1xxx / 4xxx</td></tr>
 *     <tr><td>用户可见消息</td><td>✅ 展示 {@code e.getMessage()}</td><td>⚠️ 仅展示"系统繁忙"</td></tr>
 *     <tr><td>是否重试</td><td>否</td><td>视情况（如超时）</td></tr>
 * </table>
 *
 * <p><b>设计定位：</b>继承 {@link RuntimeException}——系统异常由
 * {@code GlobalExceptionHandler} 统一捕获，<b>ERROR 级别日志 + 触发告警</b>。
 *
 * <p><b>用户可见性：</b>{@link #getMessage()} 可能包含内部信息（如"调用 user-service 超时"、
 * "数据库连接池耗尽"），<b>不应</b>直接展示给终端用户。全局处理器应将用户侧消息
 * 统一为"系统繁忙，请稍后再试"，详细信息通过 traceId 查询日志。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 依赖服务调用失败（带根因）
 * try {
 *     remoteUserService.getUser(id);
 * } catch (FeignException e) {
 *     throw SystemException.of(ErrorCode.REMOTE_SERVICE_ERROR, e);
 * }
 *
 * // 2. 依赖服务超时（需要运维关注）
 * throw SystemException.of(ErrorCode.REMOTE_TIMEOUT,
 *         "调用 user-service 超时，耗时 5s");
 *
 * // 3. 数据不一致（自定义消息）
 * throw SystemException.of(ErrorCode.SYSTEM_ERROR,
 *         "用户 " + id + " 的余额与流水不一致");
 * }</pre>
 *
 * @author QI Guang
 * @see BusinessException
 * @see ErrorCode
 * @since 1.0.0
 */
public class SystemException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码（不可变）。
     *
     * <p>通常使用 1xxx（系统级）或 4xxx（依赖级）区段的错误码。
     */
    private final ErrorCode errorCode;

    // ==================== 构造器（私有，强制使用静态工厂） ====================

    private SystemException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode 不能为 null").getMessage());
        this.errorCode = errorCode;
    }

    private SystemException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null ? customMessage
                : Objects.requireNonNull(errorCode, "errorCode 不能为 null").getMessage());
        this.errorCode = errorCode;
    }

    private SystemException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode 不能为 null").getMessage(), cause);
        this.errorCode = errorCode;
    }

    private SystemException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage != null ? customMessage
                : Objects.requireNonNull(errorCode, "errorCode 不能为 null").getMessage(), cause);
        this.errorCode = errorCode;
    }

    // ==================== 静态工厂 ====================

    /**
     * 使用 {@link ErrorCode} 默认消息创建。
     *
     * @param errorCode 错误码，不允许为 {@code null}
     * @return SystemException 实例
     */
    public static SystemException of(ErrorCode errorCode) {
        return new SystemException(errorCode);
    }

    /**
     * 使用自定义消息覆盖 {@link ErrorCode} 默认消息创建。
     *
     * <p><b>推荐：</b>系统异常的自定义消息应包含<b>定位信息</b>——服务名、
     * 关键参数、耗时，便于运维排查。
     *
     * @param errorCode     错误码，不允许为 {@code null}
     * @param customMessage 自定义消息；为 {@code null} 时使用 {@link ErrorCode#getMessage()}
     * @return SystemException 实例
     */
    public static SystemException of(ErrorCode errorCode, String customMessage) {
        return new SystemException(errorCode, customMessage);
    }

    /**
     * 使用 {@link ErrorCode} 默认消息 + 根因创建。
     *
     * <p><b>推荐：</b>包装底层异常时保留原始 cause，避免丢失堆栈。
     *
     * @param errorCode 错误码，不允许为 {@code null}
     * @param cause     根因，允许为 {@code null}
     * @return SystemException 实例
     */
    public static SystemException of(ErrorCode errorCode, Throwable cause) {
        return new SystemException(errorCode, cause);
    }

    /**
     * 使用自定义消息 + 根因创建。
     *
     * @param errorCode     错误码，不允许为 {@code null}
     * @param customMessage 自定义消息；为 {@code null} 时使用 {@link ErrorCode#getMessage()}
     * @param cause         根因，允许为 {@code null}
     * @return SystemException 实例
     */
    public static SystemException of(ErrorCode errorCode, String customMessage, Throwable cause) {
        return new SystemException(errorCode, customMessage, cause);
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
