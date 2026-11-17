package cn.realm.cloud.workforce.identity.internal.role.events;

/**
 * 角色删除事件。
 *
 * <p>在 {@code role} 子域内角色成功逻辑删除后发布。删除后角色实体已不存在，
 * 订阅方只能凭 {@code id} 清理自己持有的引用，因此只带 ID。
 *
 * <p><b>订阅方是同模块下的 {@code user} 子域：</b>
 * {@code user} 子域的 {@code RoleDeletedListener} 监听本事件，清理
 * {@code iam_user_role} 中指向该角色的关联记录。
 * 这是 {@code role → events → user} 的单向内部依赖链，
 * 避免了 {@code role} 在删除时直接操作 {@code user} 子域的表（防止双向依赖）。
 *
 * <p><b>作用域：</b>本事件是 {@code realm-workforce-identity-server} 模块
 * <b>内部</b>的具名接口（Named Interface），<b>不</b>对外部 Maven 模块暴露。
 *
 * <p><b>时序敏感性：</b>订阅方必须用 {@code @ApplicationModuleListener}——
 * 删除事件在事务回滚时若已被同步处理，会导致“用户角色关联被清空但角色还在”的数据不一致。
 *
 * @param id 角色 ID
 * @author QI Guang
 */
public record RoleDeletedEvent(
        Long id
) {
}
