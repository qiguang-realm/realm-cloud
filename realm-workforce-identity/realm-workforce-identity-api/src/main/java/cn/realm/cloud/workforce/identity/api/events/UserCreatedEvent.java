package cn.realm.cloud.workforce.identity.api.events;

/**
 * 用户创建事件。
 *
 * <p>在 {@code user} 子域内用户成功创建后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code realm-workforce-identity-api} 模块
 * 公开契约的一部分，其他业务模块（如 realm-analytics、realm-audit-logging）
 * 通过 {@code @ApplicationModuleListener} 异步消费。
 *
 * <p>事件仅携带订阅方完成反应所必需的<b>标识与关键事实</b>：
 * 用户 ID、用户名、所属部门 ID。不嵌入 {@code UserDO} 或任何可变对象，
 * 避免订阅方隐式依赖发布方的内部数据结构。
 *
 * @param id           用户 ID
 * @param username     用户名（登录标识）
 * @param departmentId 所属部门 ID，可为 {@code null}
 * @author QI Guang
 */
public record UserCreatedEvent(
        Long id,
        String username,
        Long departmentId
) {
}
