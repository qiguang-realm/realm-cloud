package cn.realm.cloud.system.server.role.internal.application;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 角色-菜单关联的应用服务。
 *
 * <p>编排三个用例：
 * <ul>
 *   <li>查询单个角色已分配的菜单 ID 列表</li>
 *   <li>批量查询一批角色授权的菜单 ID 并集</li>
 *   <li>全量替换角色的菜单授权</li>
 * </ul>
 * 与 {@link RoleDepartmentService} 的结构完全对称：查询 + 全量替换。
 * 唯一的差异是本服务<b>不做</b>额外的 {@code dataScope} 校验——菜单授权
 * 与数据范围无关，任何 {@code dataScope} 的角色都可以分配菜单。
 *
 * <p><b>模块可见性：</b>本接口位于 {@code internal} 包，<b>不暴露给其他模块</b>。
 * 其他模块需要“角色可访问哪些菜单”时，通过模块根包的 {@code RoleFacade}
 * 访问，而不是注入本接口。
 *
 * <p><b>与 {@code RoleService} 的职责边界：</b>{@code RoleService.deleteRole}
 * 在删除角色时直接调用 {@code RoleMenuRepository.deleteByRoleId} 清理关联，
 * <b>不</b>经过本接口——删除是角色的生命周期事件，不是“替换菜单授权”的用例。
 *
 * @author QI Guang
 */
public interface RoleMenuService {

    /**
     * 查询单个角色已分配的菜单 ID 列表。
     *
     * <p>如果角色不存在，返回空列表而不是抛异常——查询接口对不存在的
     * 资源返回空是更宽容的语义，调用方（前端回显）不需要为“角色刚被删除”
     * 这一边界单独处理。这与 {@link RoleDepartmentService#findDepartmentIdsByRoleId(Long)}
     * 的宽容策略一致。
     *
     * <p><b>使用场景：</b>角色编辑页回显菜单勾选状态。查询单个角色时用这个方法。
     *
     * @param roleId 角色 ID；为 {@code null} 时返回空列表
     * @return 菜单 ID 列表；角色不存在或无关联时返回空列表
     */
    List<Long> findMenuIdsByRoleId(Long roleId);

    /**
     * 批量查询一批角色授权的菜单 ID 并集。
     *
     * <p>供 {@code RoleFacade.findMenuIdsByRoleIds} 使用，进而支撑
     * {@code UserFacade.findPermissionsByUserId} 的聚合链路：
     * 用户 → 角色 → 菜单 → 权限点。
     *
     * <p><b>与 {@link #findMenuIdsByRoleId(Long)} 的区别：</b>
     * <ul>
     *   <li>{@code findMenuIdsByRoleId} 查单个角色，供角色编辑页回显使用</li>
     *   <li>本方法查一批角色的并集，供登录时权限点聚合使用；一次
     *       {@code IN} 查询完成，避免 N 次单角色查询</li>
     * </ul>
     *
     * <p><b>不校验角色是否存在：</b>调用方（{@code UserFacade.findPermissionsByUserId}）
     * 从 {@code iam_user_role} 拿到的 {@code roleIds} 理论上都是有效角色；
     * 即使有悬空 ID（角色已删除但关联未清理），也只是查不到授权、返回空集合，
     * 不会报错。这种“宽容查询”让聚合链路更健壮——单个悬空 ID 不会阻断
     * 整个用户的权限点计算。
     *
     * <p><b>缓存提醒：</b>本方法会被登录流程频繁调用（每个请求都可能触发，
     * 取决于 {@code SystemLoginUserProvider.loadUserById} 是否有缓存）。
     * 生产环境应在 {@code SystemLoginUserProvider} 层按 {@code userId} 缓存
     * {@code LoginUser}（含 {@code permissions}），失效时机与
     * {@code RoleDeletedEvent} / {@code MenuDeletedEvent} /
     * {@code RoleMenuChangedEvent} 联动，而不是在本方法内部缓存——
     * 缓存粒度按用户比按角色集合更合理。
     *
     * @param roleIds 角色 ID 集合；为 {@code null} 或空集合时返回空集合
     * @return 菜单 ID 集合；无授权时返回空集合
     */
    Set<Long> findMenuIdsByRoleIds(Collection<Long> roleIds);

    /**
     * 全量替换角色的菜单授权。
     *
     * <p>语义是“设置角色拥有这些菜单”，而不是逐条增删：
     * 服务端先删除 {@code iam_role_menu} 中该角色的所有关联，
     * 再插入新列表。两个步骤在同一事务内完成，任一步失败整体回滚。
     *
     * <p><b>服务端负责：</b>
     * <ol>
     *   <li>校验角色存在</li>
     *   <li>校验 {@code menuIds} 中的每个 ID 对应一个未删除的菜单
     *       （通过 {@code MenuFacade.existsByIds}）</li>
     *   <li>去重 {@code menuIds}，避免撞唯一索引</li>
     * </ol>
     *
     * <p><b>空列表是合法的</b>——表示清空该角色的所有菜单授权。
     * 此时角色在权限校验时没有任何菜单/按钮权限，前端应在角色编辑页
     * 提示用户补充授权。
     *
     * <p><b>幂等性：</b>同一请求重复执行的结果与执行一次相同——
     * 先删后插的语义天然幂等。这保证了 Event Publication Registry
     * 重试或前端重复提交时不会产生重复关联。
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 列表；空列表表示清空授权；
     *                为 {@code null} 时视为空列表
     * @throws IllegalArgumentException 角色不存在、菜单 ID 无效时抛出
     */
    void replaceRoleMenus(Long roleId, List<Long> menuIds);
}
