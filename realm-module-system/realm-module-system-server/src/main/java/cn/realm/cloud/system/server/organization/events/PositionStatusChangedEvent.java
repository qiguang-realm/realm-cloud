package cn.realm.cloud.system.server.organization.events;

/**
 * 岗位状态变更事件。
 *
 * <p>在 {@code organization} 模块内岗位状态成功变更后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code organization} 模块公开 API 的一部分，
 * 其他模块通过 {@code allowedDependencies} 中的
 * {@code "organization::events"} 依赖，而不是直接引用 {@code organization}
 * 模块的其他类型。
 *
 * <p><b>发布时机：</b>由 {@code PositionServiceImpl.changeStatus} 在事务内
 * 发布。状态未实际变化（新旧状态相同）时<b>不发布</b>——订阅方不会收到
 * “状态从 1 变为 1” 这类无意义的通知。
 *
 * <p><b>订阅方式：</b>订阅方必须使用
 * {@code @ApplicationModuleListener}（或至少
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}），
 * <b>不要</b>使用裸 {@code @EventListener}。前者保证事件在岗位状态变更的
 * 事务提交成功之后才被处理；后者会在事务提交前同步执行，可能读到
 * 未提交的数据，或事务回滚后副作用已经发生。
 *
 * <p><b>字段设计：</b>只携带岗位 ID 和变更后的新状态。不携带
 * {@code PositionDO} 或完整岗位快照——订阅方如需更多字段，用
 * {@code id} 回调 {@code OrganizationFacade} 的窄查询接口。
 *
 * @param id     岗位 ID
 * @param status 变更后的新状态：0-正常、1-停用
 * @author QI Guang
 */
public record PositionStatusChangedEvent(
        Long id,
        Integer status
) {
}
