package cn.realm.cloud.system.server.menu.events;

/**
 * 菜单状态变更事件。
 *
 * <p>在 {@code menu} 模块内菜单状态成功变更后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code menu} 模块公开 API 的一部分，
 * 其他模块通过 {@code allowedDependencies} 中的
 * {@code "menu::events"} 依赖。
 *
 * <p><b>发布时机：</b>由 {@code MenuServiceImpl.changeStatus} 在事务内发布。
 * 状态未实际变化（新旧状态相同）时<b>不发布</b>——{@code changeStatus}
 * 在写库前用 {@code Objects.equals(menu.getStatus(), status)} 短路，
 * 订阅方不会收到“状态从 1 变为 1”这类无意义的通知。
 *
 * <p><b>订阅方式：</b>与 {@link MenuDeletedEvent} 相同，订阅方必须使用
 * {@code @ApplicationModuleListener}，不要用裸 {@code @EventListener}。
 *
 * <p><b>字段设计：</b>只携带菜单 ID 和变更后的新状态。不携带
 * {@code MenuDO} 或完整菜单快照——订阅方如需更多字段，用 {@code id}
 * 回调 {@code MenuFacade} 的窄查询接口。注意：回调时读到的是
 * <b>当前</b>状态，如果订阅方需要“变更前的状态”，应通过
 * {@code MenuFacade} 在事件处理中另行处理，或者要求本事件扩展为
 * {@code (id, oldStatus, newStatus)}——当前没有这个需求，保持最窄。
 *
 * @param id     菜单 ID
 * @param status 变更后的新状态：0-正常、1-停用
 * @author QI Guang
 */
public record MenuStatusChangedEvent(
        Long id,
        Integer status
) {
}
