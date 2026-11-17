package cn.realm.cloud.workforce.identity.api.events;

/**
 * 用户状态变更事件。
 *
 * <p>在 {@code user} 子域内用户状态成功变更后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code realm-workforce-identity-api} 模块
 * 公开契约的一部分。
 *
 * <p><b>典型消费场景（安全关键）：</b>
 * 当用户被停用（{@code status} 变为 1）时，认证/授权模块或网关
 * 必须监听此事件并立即清理该用户的会话缓存或令牌黑名单，
 * 防止停用用户在旧 Token 过期前继续访问系统。
 *
 * <p>事件仅携带标识与新状态值：用户 ID、变更后的状态。不嵌入
 * 完整用户快照，订阅方如需更多字段，用 {@code id} 回调
 * {@code IdentityFacade} 的窄查询接口。
 *
 * @param id     用户 ID
 * @param status 变更后的新状态值（0-正常，1-停用）
 * @author QI Guang
 */
public record UserStatusChangedEvent(
        Long id,
        Integer status
) {
}
