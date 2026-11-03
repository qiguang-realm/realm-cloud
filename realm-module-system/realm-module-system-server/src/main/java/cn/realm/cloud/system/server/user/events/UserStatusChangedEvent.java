package cn.realm.cloud.system.server.user.events;

/**
 * 用户状态变更事件。
 *
 * <p>在 {@code user} 模块内用户状态成功变更后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code user} 模块公开 API 的一部分，
 * 其他模块通过 {@code allowedDependencies} 中的 {@code "user::events"}
 * 依赖，而不是直接引用 {@code user} 模块的其他类型。
 *
 * <p>事件仅携带标识与新状态值：用户 ID、变更后的状态。不嵌入
 * {@code UserDO} 或完整用户快照，订阅方如需更多字段，用 {@code id}
 * 回调 {@code UserFacade} 的窄查询接口。
 *
 * @param id     用户 ID
 * @param status 变更后的新状态值
 * @author QI Guang
 */
public record UserStatusChangedEvent(
        Long id,
        Integer status
) {
}
