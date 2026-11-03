package cn.realm.cloud.system.server.user.internal.persistence;

import java.util.List;

/**
 * 用户-角色关联仓储。
 *
 * <p>方法围绕“按用户操作角色”设计，不暴露 MyBatis-Plus 的 {@code Wrapper}。
 * 关联表没有独立于用户的业务生命周期，所有操作都以 {@code userId} 为入口。
 *
 * <p>关联表不使用逻辑删除和乐观锁——全量替换的语义是“物理删除旧记录、插入新记录”，
 * 历史授权变更靠审计日志追溯，不靠表内保留已删除行。
 *
 * @author QI Guang
 */
public interface UserRoleRepository {

    /**
     * 查询用户已分配的角色 ID 列表。
     *
     * @param userId 用户 ID；为 {@code null} 时返回空列表
     * @return 角色 ID 列表；无关联时返回空列表
     */
    List<Long> findRoleIdsByUserId(Long userId);

    /**
     * 全量替换用户的角色分配：先删除该用户的所有关联，再批量插入新记录。
     *
     * <p>调用方必须确保在事务内调用，否则删除成功、插入失败会留下用户
     * 没有任何角色的中间状态。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表；为 {@code null} 或空列表时只执行删除
     */
    void replaceUserRoles(Long userId, List<Long> roleIds);

    /**
     * 删除用户的所有角色关联。
     *
     * <p>用于两种场景：删除用户时的关联清理；用户被停用后需要解除授权时。
     */
    void deleteByUserId(Long userId);

    /**
     * 删除指定角色的所有用户关联。
     *
     * <p>用于 {@code RoleDeletedListener} 监听 {@code RoleDeletedEvent} 时
     * 清理残留关联，避免角色删除后 {@code iam_user_role} 留下悬空记录。
     */
    void deleteByRoleId(Long roleId);
}
