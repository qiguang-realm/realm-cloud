package cn.realm.cloud.system.server.menu.events;

/**
 * 菜单删除事件。
 *
 * <p>在 {@code menu} 模块内菜单成功逻辑删除后发布，作为跨模块的
 * <b>事实通知</b>。本类型是 {@code menu} 模块公开 API 的一部分，
 * 其他模块通过 {@code allowedDependencies} 中的
 * {@code "menu::events"} 依赖，而不是直接引用 {@code menu} 模块的其他类型。
 *
 * <p><b>为什么只带 {@code id}：</b>删除后菜单实体已不存在，订阅方只能凭
 * {@code id} 清理自己持有的引用（如 {@code iam_role_menu} 中的角色授权），
 * 因此不携带任何菜单快照字段。携带 {@code MenuDO} 或 {@code MenuResponse}
 * 只会让订阅方误以为可以读取已删除菜单的数据。
 *
 * <p><b>订阅方是 {@code role} 模块：</b>{@code role} 模块的
 * {@code MenuDeletedListener} 监听本事件，调用
 * {@code RoleMenuRepository.deleteByMenuId(menuId)} 清理
 * {@code iam_role_menu} 中指向该菜单的授权记录。这是
 * {@code menu → events → role} 的单向依赖链，避免了 {@code menu}
 * 在删除时反向查询 {@code role} 持有的表——那会与
 * {@code role → menu::events} 形成循环。
 *
 * <p><b>时序敏感性：</b>本事件由 {@code MenuServiceImpl.deleteMenu}
 * 在事务内发布。订阅方<b>必须</b>使用 {@code @ApplicationModuleListener}
 * （或至少 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}），
 * <b>禁止</b>使用裸 {@code @EventListener}——后者会在事务提交前同步执行，
 * 一旦删除事务回滚，角色侧的授权已经被订阅方清掉了，但菜单本身还在，
 * 造成数据不一致。删除事件比创建/状态变更事件更敏感：创建事件在回滚时
 * 最多是“通知了不该通知的事”，删除事件在回滚时是“清理了不该清理的引用”。
 *
 * <p><b>最终一致：</b>从菜单删除提交到 {@code role} 模块清理完成之间，
 * {@code iam_role_menu} 中可能短暂存在指向已删除菜单的记录。订阅方的
 * 查询逻辑应容忍“菜单查不到”的情况（返回 {@code null} 菜单名，
 * 或过滤掉菜单已删除的授权），清理逻辑必须幂等——事件可能因
 * Event Publication Registry 的重试而被投递多次，按 {@code menuId}
 * 删除关联的幂等性由“删除不存在的记录影响 0 行”自然保证。
 *
 * <p><b>与 {@code MenuStatusChangedEvent} 的区别：</b>状态变更事件携带
 * {@code (id, status)}，因为订阅方可能需要根据新状态调整行为；
 * 删除事件只带 {@code id}，因为删除后没有“状态”可读。
 *
 * @param id 菜单 ID
 * @author QI Guang
 */
public record MenuDeletedEvent(
        Long id
) {
}
