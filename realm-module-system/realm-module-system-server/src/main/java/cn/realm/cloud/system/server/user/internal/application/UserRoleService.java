package cn.realm.cloud.system.server.user.internal.application;

import java.util.List;

/**
 * 用户-角色分配应用服务。
 *
 * <p>管理 {@code iam_user_role} 关联表：表达用户被授予了哪些角色。
 * 关联表归属 {@code user} 模块——用户在系统里“拥有”哪些角色，本质上是
 * 用户画像的一部分，用户管理界面是这类关联写入的主要入口。
 *
 * <p>角色的合法性通过 {@code RoleFacade.existsByIds} 校验，不直接依赖
 * {@code role} 的 {@code internal}。角色被删除后的残留关联由
 * {@code RoleDeletedListener} 监听 {@code RoleDeletedEvent} 清理，保证最终一致。
 *
 * <p>写操作由 {@code UserAdminController} 的 {@code PUT /{userId}/roles}
 * 端点驱动；不存在独立的 {@code UserRoleController}——关联表没有独立于
 * 用户的业务生命周期。
 *
 * @author QI Guang
 */
public interface UserRoleService {

    /**
     * 查询用户已分配的角色 ID 列表。
     *
     * @param userId 用户 ID；为 {@code null} 时返回空列表
     * @return 角色 ID 列表；无关联时返回空列表
     */
    List<Long> findRoleIdsByUserId(Long userId);

    /**
     * 全量替换用户的角色分配。
     *
     * <p>写入前校验两件事：用户存在、{@code roleIds} 是否<b>全部</b>有效
     * （未删除的角色）。{@code RoleFacade.existsByIds} 的语义是“全部存在”，
     * 不是“存在任意一个”——后者会让 {@code [有效ID, 无效ID]} 通过校验，
     * 无效的 {@code roleId} 被写入关联表。
     *
     * <p>“先删后插”在事务内完成，避免删除成功、插入失败留下用户没有任何
     * 角色的中间状态。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表；为 {@code null} 或空列表时清空该用户的所有角色
     * @throws IllegalArgumentException 用户不存在、角色 ID 无效
     */
    void replaceUserRoles(Long userId, List<Long> roleIds);
}
