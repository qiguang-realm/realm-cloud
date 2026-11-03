package cn.realm.cloud.system.server.user.events;

/**
 * 用户删除事件。
 *
 * <p>在 {@code user} 模块内用户成功删除后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code user} 模块公开 API 的一部分，
 * 其他模块通过 {@code allowedDependencies} 中的 {@code "user::events"}
 * 依赖，而不是直接引用 {@code user} 模块的其他类型。
 *
 * <p>事件仅携带用户 ID。删除后用户实体已不存在，订阅方只能凭
 * {@code id} 清理自己持有的引用（缓存、关联记录、审计索引等），
 * 因此不再携带任何用户快照字段。
 *
 * @param id 用户 ID
 * @author QI Guang
 */
public record UserDeletedEvent(
        Long id
) {
}
