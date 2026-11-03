package cn.realm.cloud.system.server.organization.events;

/**
 * 部门删除事件。
 *
 * <p>在 {@code organization} 模块内部门成功逻辑删除后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code organization} 模块公开 API 的一部分，
 * 其他模块通过 {@code allowedDependencies} 中的
 * {@code "organization::events"} 依赖，而不是直接引用 {@code organization}
 * 模块的其他类型。
 *
 * <p><b>为什么删除事件只带 ID：</b>删除后部门实体已不存在，订阅方
 * 只能凭 {@code id} 清理自己持有的引用（缓存、关联记录、审计索引等），
 * 因此不再携带任何部门快照字段。携带 {@code DepartmentDO} 或
 * {@code DepartmentResponse} 只会让订阅方误以为可以读取已删除部门的数据。
 *
 * <p><b>订阅方是 {@code user} 模块：</b>{@code user} 模块的
 * {@code DepartmentDeletedListener} 监听本事件，清理 {@code iam_user}
 * 中指向该部门的引用（将 {@code department_id} 置为 {@code null}，
 * 或按业务规则处理）。这是 {@code organization → events → user} 的
 * 单向依赖链，避免了 {@code organization} 在删除时反向查询
 * {@code iam_user} 表。
 *
 * <p><b>时序敏感性：</b>本事件由 {@code DepartmentServiceImpl.deleteDepartment}
 * 在事务内发布。订阅方<b>必须</b>使用 {@code @ApplicationModuleListener}
 * ——删除事件比创建/状态变更事件更敏感：如果订阅方误用同步
 * {@code @EventListener}，它会在 {@code deleteById} 之后、事务提交之前
 * 执行；一旦事务回滚，用户的 {@code department_id} 已经被订阅方清空了，
 * 但部门本身还在，造成数据不一致。
 *
 * <p><b>最终一致：</b>从部门删除提交到 {@code user} 模块清理完成之间，
 * {@code iam_user.department_id} 可能短暂指向已删除的部门。订阅方的
 * 查询逻辑应容忍“部门查不到”的情况（返回 {@code null} 部门名，
 * 或过滤掉部门已删除的用户），清理逻辑必须幂等——事件可能因
 * Event Publication Registry 的重试而被投递多次。
 *
 * @param id 部门 ID
 * @author QI Guang
 */
public record DepartmentDeletedEvent(
        Long id
) {
}
