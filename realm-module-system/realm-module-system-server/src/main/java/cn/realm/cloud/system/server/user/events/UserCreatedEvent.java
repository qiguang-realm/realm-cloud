package cn.realm.cloud.system.server.user.events;

/**
 * 用户创建事件。
 *
 * <p>在 {@code user} 模块内用户成功创建后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code user} 模块公开 API 的一部分，
 * 其他模块通过 {@code allowedDependencies 中的 "user::events"}
 * 依赖，而不是直接引用 {@code user} 模块的其他类型。
 *
 * <p>事件仅携带订阅方完成反应所必需的<b>标识与关键事实</b>：
 * 用户 ID、用户名、所属部门 ID。不嵌入 {@code UserDO}、
 * {@code UserDto} 或任何可变对象，避免订阅方隐式依赖发布方的
 * 内部数据结构。
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
