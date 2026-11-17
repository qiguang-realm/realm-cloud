package cn.realm.cloud.workforce.identity.api.events;

/**
 * 用户删除事件。
 *
 * <p>在 {@code user} 子域内用户成功删除后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code realm-workforce-identity-api} 模块
 * 公开契约的一部分，其他业务模块（如 realm-analytics、realm-audit-logging）
 * 通过 {@code @ApplicationModuleListener} 异步消费。
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
