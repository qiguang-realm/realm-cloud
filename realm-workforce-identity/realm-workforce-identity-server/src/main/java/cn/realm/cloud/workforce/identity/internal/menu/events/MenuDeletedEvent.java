package cn.realm.cloud.workforce.identity.internal.menu.events;

/**
 * 菜单删除事件。
 *
 * <p>在 {@code menu} 子域内菜单成功逻辑删除后发布，作为同 Maven 模块内
 * 其他子域（如 {@code role}）的<b>事实通知</b>。
 *
 * <p><b>作用域：</b>本事件是 {@code realm-workforce-identity-server} 模块
 * <b>内部</b>的具名接口（Named Interface），<b>不</b>对外部 Maven 模块暴露。
 * 同模块的 {@code role} 子域通过 {@code allowedDependencies} 中的
 * {@code "menu :: events"} 依赖本包。
 *
 * <p><b>为什么只带 {@code id}：</b>删除后菜单实体已不存在，订阅方只能凭
 * {@code id} 清理自己持有的引用（如 {@code iam_role_menu} 中的角色授权），
 * 因此不携带任何菜单快照字段。
 *
 * <p><b>时序敏感性：</b>本事件由 {@code MenuCommandService.deleteMenu}
 * 在事务内发布。订阅方<b>必须</b>使用 {@code @ApplicationModuleListener}
 * （或至少 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}）。
 * <b>禁止</b>使用裸 {@code @EventListener}——后者会在事务提交前同步执行，
 * 一旦删除事务回滚，角色侧的授权已经被订阅方清掉了，但菜单本身还在，
 * 造成数据不一致。删除事件在回滚时是“清理了不该清理的引用”。
 *
 * <p><b>最终一致与幂等性：</b>订阅方的查询逻辑应容忍“菜单查不到”的情况，
 * 清理逻辑必须幂等——事件可能因重试而被投递多次，按 {@code menuId}
 * 删除关联的幂等性由“删除不存在的记录影响 0 行”自然保证。
 *
 * @param id 菜单 ID
 * @author QI Guang
 */
public record MenuDeletedEvent(
        Long id
) {
}
