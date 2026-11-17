package cn.realm.cloud.framework.core.exception;

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
 *     <tr><td>HTTP 状态</td>
 *         <td>4xx（由 {@code realm-web} 的 {@code ErrorCodeHttpStatusMapping}
 *             按 ErrorCode 映射）</td>
 *         <td>5xx（由同一映射表映射：500 / 502 / 503 / 504）</td></tr>
 *     <tr><td>日志级别</td><td>WARN</td><td>ERROR（触发告警）</td></tr>
 *     <tr><td>典型 code 段位</td>
 *         <td>2xxx（业务级）；也可携带 1xxx 框架级通用码
 *             （如 {@link FrameworkErrorCode#PARAM_INVALID}）</td>
 *         <td>1xxx（系统故障）/ 4xxx（依赖级）/ 5xxx（数据访问）</td></tr>
 *     <tr><td>用户可见消息</td><td>✅ 展示 {@code e.getMessage()}</td><td>⚠️ 仅展示"系统繁忙"</td></tr>
 *     <tr><td>是否重试</td><td>否</td><td>视情况（如超时）</td></tr>
 * </table>
 *
 * <p><b>设计定位：</b>继承 {@link RuntimeException}——系统异常由 {@code realm-web} 的
 * {@code GlobalExceptionHandler} 统一捕获，<b>ERROR 级别日志 + 触发告警</b>，
 * 按 ADR-002 路径 A 转换为 <b>RFC 9457 {@code ProblemDetail}</b>
 * （{@code realm.web.error.format=api-response} 时由 {@code ApiResponseBodyAdvice}
 * 包装为 {@code ApiResponse}）。
 *
 * <p><b>HTTP 状态映射：</b>由 {@code realm-web} 的 {@code ErrorCodeHttpStatusMapping}
 * 负责把 {@link ErrorCode} 映射为 HTTP 状态。映射关系是<b>传输层职责</b>，
 * {@link ErrorCode} 本身不携带 HTTP 语义——与 Google `Status` 模型的
 * “逻辑错误模型与传输协议解耦”一致。
 *
 * <p><b>框架级映射（由 {@code ErrorCodeHttpStatusMapping} 内置）：</b>
 * <ul>
 *     <li>{@link FrameworkErrorCode#SYSTEM_ERROR} → 500</li>
 *     <li>{@link FrameworkErrorCode#REMOTE_SERVICE_ERROR} → 503</li>
 *     <li>{@link FrameworkErrorCode#REMOTE_BAD_GATEWAY} → 502</li>
 *     <li>{@link FrameworkErrorCode#REMOTE_TIMEOUT} → 504</li>
 *     <li>{@link FrameworkErrorCode#DATA_CONNECTION_EXHAUSTED} → 503</li>
 * </ul>
 * 业务代码无需关心 HTTP 状态，只需选择正确的 {@link ErrorCode}。
 *
 * <p><b>用户可见性：</b>{@link #getMessage()} 可能包含内部信息（如"调用 user-service 超时"、
 * "数据库连接池耗尽"），<b>不应</b>直接展示给终端用户。全局处理器应将用户侧消息
 * 统一为"系统繁忙，请稍后再试"，详细信息通过 traceId 查询日志。
 * {@code ProblemDetailAssembler.fromSystem(e, request, userMessage)} 的
 * {@code userMessage} 参数就是这个“用户侧通用消息”。
 *
 * <p><b>{@link ErrorCode} 实现的可序列化性：</b>
 * {@code SystemException} 实现了 {@link java.io.Serializable}，
 * 其 {@code errorCode} 字段是 {@link ErrorCode} 接口类型。
 * 在异常需要跨进程传递的场景（如 RMI、分布式事务回传），
 * {@link ErrorCode} 的实现必须可序列化。**推荐所有实现使用枚举**——
 * 枚举天然实现 {@link java.io.Serializable}，且序列化后反序列化能保持单例语义。
 * 如果使用非枚举实现，需自行确保其可序列化，否则序列化时会抛
 * {@link java.io.NotSerializableException}。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 1. 依赖服务调用失败（带根因）
 * try {
 *     remoteUserService.getUser(id);
 * } catch (FeignException e) {
 *     throw SystemException.of(FrameworkErrorCode.REMOTE_SERVICE_ERROR, e);
 * }
 *
 * // 2. 依赖服务超时（需要运维关注）
 * throw SystemException.of(FrameworkErrorCode.REMOTE_TIMEOUT,
 *         "调用 user-service 超时，耗时 5s");
 *
 * // 3. 数据不一致（自定义消息）
 * throw SystemException.of(FrameworkErrorCode.SYSTEM_ERROR,
 *         "用户 " + id + " 的余额与流水不一致");
 * }</pre>
 *
 * @author QI Guang
 * @see BusinessException
 * @see ErrorCode
 * @see FrameworkErrorCode
 */
public class SystemException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码（不可变）。
     *
     * <p>通常使用 1xxx（系统级）、4xxx（依赖级）或 5xxx（数据访问级）
     * 区段的错误码，由 {@link FrameworkErrorCode} 提供。
     *
     * <p><b>不携带 HTTP 语义：</b>HTTP 状态由 {@code realm-web} 的
     * {@code ErrorCodeHttpStatusMapping} 在传输层映射。
     */
    private final ErrorCode errorCode;

    // ==================== 构造器（私有，强制使用静态工厂） ====================

    private SystemException(ErrorCode errorCode) {
        super(resolveMessage(errorCode, null));
        this.errorCode = errorCode;
    }

    private SystemException(ErrorCode errorCode, String customMessage) {
        super(resolveMessage(errorCode, customMessage));
        this.errorCode = errorCode;
    }

    private SystemException(ErrorCode errorCode, Throwable cause) {
        super(resolveMessage(errorCode, null), cause);
        this.errorCode = errorCode;
    }

    private SystemException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(resolveMessage(errorCode, customMessage), cause);
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

    // ==================== 内部辅助 ====================

    /**
     * 解析异常消息：优先使用 {@code customMessage}，否则使用
     * {@link ErrorCode#getMessage()}。
     *
     * <p><b>为什么提取为独立方法：</b>与 {@link BusinessException} 一致——
     * 四个构造器都需要做同样的“{@code errorCode} 非空校验 +
     * {@code customMessage} 回退”逻辑。提取后构造器只保留一行
     * {@code super(...)} 调用，避免同一段逻辑复制四份。
     * 未来引入 {@code MessageSource} 本地化时，只改这一处。
     *
     * <p><b>{@link ErrorCode#getMessage()} 的非空保护：</b>
     * {@link ErrorCode} 接口的 Javadoc 承诺 {@code getMessage()} 永不为
     * {@code null}。如果某个实现违反这一契约，此处会立即抛出
     * {@link NullPointerException}，异常消息包含实现类名，便于定位。
     *
     * <p><b>为什么是 static：</b>本方法在 {@code super(...)} 调用中执行，
     * 此时实例字段尚未初始化，不能使用实例方法。
     *
     * @param errorCode     错误码，不允许为 {@code null}
     * @param customMessage 自定义消息，允许为 {@code null}
     * @return 非空的消息字符串
     * @throws NullPointerException {@code errorCode} 为 {@code null}，
     *                              或 {@code errorCode.getMessage()} 返回 {@code null}
     */
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
