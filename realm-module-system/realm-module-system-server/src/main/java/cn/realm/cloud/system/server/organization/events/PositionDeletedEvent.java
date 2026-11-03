package cn.realm.cloud.system.server.organization.events;

/**
 * 岗位删除事件。
 *
 * <p>在 {@code organization} 模块内岗位成功逻辑删除后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code organization} 模块公开 API 的一部分，
 * 其他模块通过 {@code allowedDependencies} 中的
 * {@code "organization::events"} 依赖，而不是直接引用 {@code organization}
 * 模块的其他类型。
 *
 * <p><b>为什么删除事件只带 ID：</b>删除后岗位实体已不存在，订阅方
 * 只能凭 {@code id} 清理自己持有的引用（缓存、关联记录、审计索引等），
 * 因此不再携带任何岗位快照字段。携带 {@code PositionDO} 或
 * {@code PositionResponse} 只会让订阅方误以为可以读取已删除岗位的数据。
 *
 * <p><b>订阅方是 {@code user} 模块：</b>{@code user} 模块的
 * {@code PositionDeletedListener} 监听本事件，清理 {@code iam_user_position}
 * 中指向该岗位的关联记录。这是 {@code organization → events → user}
 * 的单向依赖链，避免了 {@code organization} 在删除时反向查询
 * {@code iam_user_position} 表——那会与 {@code user → organization}
 * 形成循环依赖，{@code verify()} 会失败。
 *
 * <p><b>与“有引用就拒绝删除”的取舍：</b>本事件意味着“岗位删除后，
 * 用户身上的岗位关联会被异步清理”，而不是“被用户引用的岗位不允许删除”。
 * 如果业务要求“被引用的岗位不允许删除”，需要先在 ADR 中重新界定
 * {@code iam_user_position} 的所有权——要么划给 {@code organization}
 * 模块，要么由 {@code user} 模块通过 {@code OrganizationFacade} 暴露
 * 一个只读的“是否被引用”查询接口，而不是让 {@code organization}
 * 直接查 {@code user} 的表。当前设计选择的是“允许删除 + 异步清理”，
 * 与部门、菜单的删除语义保持一致。
 *
 * <p><b>时序敏感性：</b>本事件由 {@code PositionServiceImpl.deletePosition}
 * 在事务内发布。订阅方<b>必须</b>使用 {@code @ApplicationModuleListener}
 * ——删除事件比创建/状态变更事件更敏感：如果订阅方误用同步
 * {@code @EventListener}，它会在 {@code deleteById} 之后、事务提交之前
 * 执行；一旦事务回滚，用户的岗位关联已经被订阅方清掉了，但岗位本身还在，
 * 造成数据不一致。
 *
 * <p><b>最终一致：</b>从岗位删除提交到 {@code user} 模块清理完成之间，
 * {@code iam_user_position} 中可能短暂存在指向已删除岗位的记录。
 * 订阅方的查询逻辑应容忍“岗位查不到”的情况（返回 {@code null} 岗位名，
 * 或过滤掉岗位已删除的关联），清理逻辑必须幂等——事件可能因
 * Event Publication Registry 的重试而被投递多次。
 *
 * @param id 岗位 ID
 * @author QI Guang
 */
public record PositionDeletedEvent(
        Long id
) {
}
