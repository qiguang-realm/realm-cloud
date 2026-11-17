package cn.realm.cloud.framework.web.exception;

import cn.realm.cloud.framework.core.exception.ErrorCode;
import cn.realm.cloud.framework.core.exception.FrameworkErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 错误码到 HTTP 状态的映射表。
 *
 * <p><b>职责：</b>把逻辑错误码翻译为 HTTP 状态。这是 <b>传输层适配</b>——
 * {@link ErrorCode} 本身不携带 HTTP 语义，映射关系在消费端维护。
 * 与 Google `Status` 模型的“逻辑错误模型与传输协议解耦”一致。
 *
 * <p><b>映射优先级：</b>
 * <ol>
 *     <li><b>业务注册映射</b>：业务模块在启动期通过
 *         {@link #register(ErrorCode, HttpStatus)} 注册</li>
 *     <li><b>框架内置映射</b>：{@link FrameworkErrorCode} 的所有码，
 *         在静态块预置</li>
 *     <li><b>兜底 500</b>：前两层都未覆盖时返回
 *         {@link HttpStatus#INTERNAL_SERVER_ERROR}——
 *         未预见的错误视为服务端问题，触发告警而非伪装成客户端错误</li>
 * </ol>
 *
 * <p><b>为什么兜底是 500 而非 400：</b>
 * 未映射的码通常意味着“新增码时忘记配映射”或“意外状态”。
 * 兜底 400 会把服务端 bug 伪装成客户端错误，掩盖问题；
 * 兜底 500 触发告警，让问题暴露出来。这与 Google 的
 * “未知错误倾向 server-side”原则一致。
 *
 * <p><b>线程安全：</b>{@code register} 只在应用启动期由自动配置调用，
 * 运行期只读。{@link ConcurrentHashMap} 保证启动期并发注册的安全。
 *
 * @author QI Guang
 * @see ErrorCode
 * @see FrameworkErrorCode
 * @see ProblemDetailAssembler
 */
public class ErrorCodeHttpStatusMapping {

    /**
     * 框架级内置映射。业务码不在此表中，由业务模块通过
     * {@link #register(ErrorCode, HttpStatus)} 注册。
     */
    private static final Map<ErrorCode, HttpStatus> FRAMEWORK_MAPPING = Map.ofEntries(
            // 1xxx 框架级
            Map.entry(FrameworkErrorCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(FrameworkErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST),
            Map.entry(FrameworkErrorCode.MISSING_PARAM, HttpStatus.BAD_REQUEST),
            Map.entry(FrameworkErrorCode.MEDIA_TYPE_UNSUPPORTED, HttpStatus.UNSUPPORTED_MEDIA_TYPE),
            Map.entry(FrameworkErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED),
            Map.entry(FrameworkErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(FrameworkErrorCode.REQUEST_TIMEOUT, HttpStatus.REQUEST_TIMEOUT),
            Map.entry(FrameworkErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(FrameworkErrorCode.CONFLICT, HttpStatus.CONFLICT),
            Map.entry(FrameworkErrorCode.UNPROCESSABLE_ENTITY, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(FrameworkErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(FrameworkErrorCode.OPTIMISTIC_LOCK_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(FrameworkErrorCode.DATA_INTEGRITY_VIOLATION, HttpStatus.INTERNAL_SERVER_ERROR),

            // 3xxx 认证授权
            Map.entry(FrameworkErrorCode.AUTH_UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
            Map.entry(FrameworkErrorCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN),
            Map.entry(FrameworkErrorCode.AUTH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED),
            Map.entry(FrameworkErrorCode.AUTH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED),
            Map.entry(FrameworkErrorCode.AUTH_TOKEN_REVOKED, HttpStatus.UNAUTHORIZED),
            Map.entry(FrameworkErrorCode.AUTH_INSUFFICIENT_SCOPE, HttpStatus.FORBIDDEN),
            Map.entry(FrameworkErrorCode.AUTH_MFA_REQUIRED, HttpStatus.FORBIDDEN),
            Map.entry(FrameworkErrorCode.AUTH_ACCOUNT_LOCKED, HttpStatus.LOCKED),
            Map.entry(FrameworkErrorCode.AUTH_CREDENTIALS_EXPIRED, HttpStatus.UNAUTHORIZED),

            // 4xxx 依赖
            Map.entry(FrameworkErrorCode.REMOTE_SERVICE_ERROR, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(FrameworkErrorCode.REMOTE_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT),
            Map.entry(FrameworkErrorCode.REMOTE_BAD_GATEWAY, HttpStatus.BAD_GATEWAY),
            Map.entry(FrameworkErrorCode.REMOTE_CIRCUIT_OPEN, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(FrameworkErrorCode.REMOTE_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS),

            // 5xxx 数据访问
            Map.entry(FrameworkErrorCode.DATA_ACCESS_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(FrameworkErrorCode.DATA_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(FrameworkErrorCode.DATA_DUPLICATE, HttpStatus.CONFLICT),
            Map.entry(FrameworkErrorCode.DATA_OPTIMISTIC_LOCK_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(FrameworkErrorCode.DATA_TRANSACTION_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(FrameworkErrorCode.DATA_CONNECTION_EXHAUSTED, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(FrameworkErrorCode.DATA_MIGRATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR),

            // 6xxx 文件 / 对象存储
            Map.entry(FrameworkErrorCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(FrameworkErrorCode.FILE_TYPE_UNSUPPORTED, HttpStatus.UNSUPPORTED_MEDIA_TYPE),
            Map.entry(FrameworkErrorCode.FILE_SIZE_EXCEEDED, HttpStatus.PAYLOAD_TOO_LARGE),
            Map.entry(FrameworkErrorCode.FILE_UPLOAD_FAILED, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(FrameworkErrorCode.FILE_PARSE_FAILED, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(FrameworkErrorCode.FILE_STORAGE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),

            // 7xxx 消息 / 调度
            Map.entry(FrameworkErrorCode.MESSAGE_PUBLISH_FAILED, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(FrameworkErrorCode.MESSAGE_CONSUME_FAILED, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(FrameworkErrorCode.MESSAGE_DUPLICATE, HttpStatus.CONFLICT),
            Map.entry(FrameworkErrorCode.SCHEDULING_JOB_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(FrameworkErrorCode.SCHEDULING_JOB_EXECUTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR),

            // 8xxx 多租户 / 配置
            Map.entry(FrameworkErrorCode.TENANT_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(FrameworkErrorCode.TENANT_CONTEXT_MISSING, HttpStatus.BAD_REQUEST),
            Map.entry(FrameworkErrorCode.TENANT_DISABLED, HttpStatus.FORBIDDEN),
            Map.entry(FrameworkErrorCode.CONFIG_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(FrameworkErrorCode.CONFIG_REFRESH_FAILED, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(FrameworkErrorCode.FEATURE_ENV_NOT_FOUND, HttpStatus.NOT_FOUND),

            // 9xxx 兜底
            Map.entry(FrameworkErrorCode.UNKNOWN_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    );

    /**
     * 业务模块注册的映射。启动期由自动配置写入，运行期只读。
     */
    private final Map<ErrorCode, HttpStatus> registeredMapping = new ConcurrentHashMap<>();

    /**
     * 注册业务模块的错误码映射。
     *
     * <p><b>调用时机：</b>业务模块的自动配置类中（构造器或
     * {@code InitializingBean.afterPropertiesSet()}），不要运行期调用。
     *
     * @param errorCode 错误码，不允许为 {@code null}
     * @param status    HTTP 状态，不允许为 {@code null}
     * @throws NullPointerException 任一参数为 {@code null} 时抛出
     */
    public void register(ErrorCode errorCode, HttpStatus status) {
        Objects.requireNonNull(errorCode, "errorCode 不能为 null");
        Objects.requireNonNull(status, "status 不能为 null");
        registeredMapping.put(errorCode, status);
    }

    /**
     * 解析错误码对应的 HTTP 状态。
     *
     * <p><b>解析顺序：</b>业务注册 → 框架内置 → 兜底 500。
     *
     * <p><b>null 处理：</b>{@code errorCode} 为 {@code null} 时返回 500，
     * 而不是抛 {@link NullPointerException}——本方法在异常处理路径上
     * 被调用，自身不应再抛异常。
     *
     * @param errorCode 错误码，允许为 {@code null}
     * @return HTTP 状态，永不为 {@code null}
     */
    public HttpStatus resolve(ErrorCode errorCode) {
        if (errorCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        HttpStatus registered = registeredMapping.get(errorCode);
        if (registered != null) {
            return registered;
        }
        HttpStatus builtIn = FRAMEWORK_MAPPING.get(errorCode);
        if (builtIn != null) {
            return builtIn;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
