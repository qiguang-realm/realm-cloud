package cn.realm.cloud.framework.core.exception;

/**
 * 错误码契约。
 *
 * <p><b>职责：</b>只定义“一个错误码必须能回答的两个问题”——
 * {@link #getCode()}（机器可读标识）与 {@link #getMessage()}（默认消息）。
 * <b>不携带任何 HTTP 语义</b>。
 *
 * <p><b>为什么不含 HTTP 状态：</b>遵循 Google `Status` 模型——
 * 错误码是语言无关、传输协议无关的逻辑标识；HTTP 状态是传输层的适配，
 * 由消费端 {@code realm-web} 维护映射关系。这样 {@code realm-core}
 * 保持零 Spring Web 依赖，业务模块也不需要关心 HTTP。
 *
 * <p><b>框架级实现：</b>{@link FrameworkErrorCode} 覆盖 1xxx / 3xxx / 4xxx /
 * 5xxx / 6xxx / 7xxx / 8xxx / 9xxx 段位的通用码。2xxx 业务码由各业务模块
 * 自己的枚举实现本接口。
 *
 * <p><b>HTTP 映射：</b>由 {@code realm-web} 的
 * {@code ErrorCodeHttpStatusMapping} 负责。未注册的码兜底为
 * 500 Internal Server Error，触发告警而非静默地当作客户端错误。
 *
 * @author QI Guang
 * @see FrameworkErrorCode
 * @see BusinessException
 * @see SystemException
 */
public interface ErrorCode {

    /**
     * 机器可读的错误码。
     *
     * <p><b>语义：</b>语言无关的稳定标识，用于程序分支判断、日志关联、
     * 网关路由、监控聚合。不随语言、环境、请求上下文变化。
     *
     * <p><b>取值约定：</b>
     * <ul>
     *     <li>{@code 0}：成功（遵循 Unix 退出码惯例）</li>
     *     <li>正数：失败，按段位分类（1xxx 框架级、2xxx 业务级、
     *         3xxx 认证授权、4xxx 依赖、5xxx 数据访问、6xxx 文件存储、
     *         7xxx 消息调度、8xxx 多租户配置、9xxx 兜底）</li>
     * </ul>
     *
     * <p><b>稳定性：</b>数值是公开 API 契约的一部分，一旦发布不应更改。
     *
     * @return 错误码数字值
     */
    int getCode();

    /**
     * 人类可读的默认消息。
     *
     * <p><b>语义：</b>错误码的默认描述文本，用于开发期调试、日志输出，
     * 以及 {@code ProblemDetailAssembler} 在无 {@code customMessage} 时的
     * 默认 {@code detail} 填充。
     *
     * <p><b>不是面向终端用户的本地化消息：</b>当前返回枚举构造时固定的文本，
     * 不随 {@code Accept-Language} 变化。未来国际化由展示层通过
     * {@code MessageSource} 按 {@link #getCode()} 解析。
     *
     * <p><b>非空契约：</b>实现必须返回非 {@code null} 值。
     *
     * @return 默认消息文本，永不为 {@code null}
     */
    String getMessage();
}
