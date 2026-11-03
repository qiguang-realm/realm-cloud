package cn.realm.cloud.system.server.role.events;

/**
 * 角色删除事件。
 *
 * <p>在 {@code role} 模块内角色成功逻辑删除后发布。删除后角色实体已不存在，
 * 订阅方只能凭 {@code id} 清理自己持有的引用，因此只带 ID。
 *
 * <p><b>订阅方是 {@code user} 模块：</b>{@code user} 模块的
 * {@code RoleDeletedListener} 监听本事件，清理 {@code iam_user_role}
 * 中指向该角色的关联。这是 {@code role → events → user} 的单向依赖链，
 * 避免了 {@code role} 在删除时反向查询 {@code iam_user_role} 表。
 *
 * <p>订阅方必须用 {@code @ApplicationModuleListener}——删除事件在事务回滚时
 * 若已被同步处理，会导致“用户角色关联被清空但角色还在”的不一致。
 *
 * @param id 角色 ID
 * @author QI Guang
 */
public record RoleDeletedEvent(
        Long id
) {
}
