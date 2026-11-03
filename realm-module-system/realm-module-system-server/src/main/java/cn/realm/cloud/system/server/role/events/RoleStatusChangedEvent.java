package cn.realm.cloud.system.server.role.events;

/**
 * 角色状态变更事件。
 *
 * <p>在 {@code role} 模块内角色状态成功变更后发布。状态未实际变化时
 * <b>不发布</b>——订阅方不会收到“状态从 1 变为 1” 这类无意义的通知。
 *
 * <p>订阅方必须使用 {@code @ApplicationModuleListener}，不要用裸
 * {@code @EventListener}——后者会在事务提交前同步执行，可能读到未提交的数据。
 *
 * @param id     角色 ID
 * @param status 变更后的新状态：0-正常、1-停用
 * @author QI Guang
 */
public record RoleStatusChangedEvent(
        Long id,
        Integer status
) {
}
