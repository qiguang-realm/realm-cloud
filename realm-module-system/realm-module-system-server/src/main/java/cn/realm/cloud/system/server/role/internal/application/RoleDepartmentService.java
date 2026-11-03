package cn.realm.cloud.system.server.role.internal.application;

import java.util.List;

/**
 * 角色-部门关联的应用服务。
 *
 * <p>编排两个用例：
 * <ul>
 *   <li>查询角色自定义数据范围的部门 ID 列表</li>
 *   <li>全量替换角色自定义数据范围的部门列表</li>
 * </ul>
 * 这两个用例只在角色 {@code dataScope = 2}（自定义）时有意义——
 * 其他数据范围下部门列表由 {@code dataScope} 本身推导，
 * {@code iam_role_department} 中的数据无意义。
 *
 * <p><b>模块可见性：</b>本接口位于 {@code internal} 包，<b>不暴露给其他模块</b>。
 * 其他模块需要“角色可访问哪些部门”时，通过模块根包的 {@code RoleFacade}
 * 访问，而不是注入本接口。
 *
 * <p><b>与 {@link RoleMenuService} 的对称性：</b>两者结构一致——都是
 * “查询关联 ID 列表 + 全量替换”。唯一的差异是本服务在替换前额外校验
 * 角色的 {@code dataScope} 必须为自定义，因为非自定义范围下部门列表无意义。
 *
 * <p><b>与 {@code RoleService} 的职责边界：</b>{@code RoleService.deleteRole}
 * 在删除角色时直接调用 {@code RoleDepartmentRepository.deleteByRoleId}
 * 清理关联，<b>不</b>经过本接口——删除是角色的生命周期事件，
 * 不是“替换部门范围”的用例。本接口只负责“设置部门范围”这一操作。
 *
 * @author QI Guang
 */
public interface RoleDepartmentService {

    /**
     * 查询角色自定义数据范围的部门 ID 列表。
     *
     * <p>仅当角色 {@code dataScope = 2}（自定义）时返回非空列表；
     * 其他数据范围（全部、本部门、本部门及以下、仅本人）下返回空列表
     * ——不是抛异常，而是返回空，因为“非自定义范围没有部门列表”
     * 是合法状态，不是错误。
     *
     * <p>如果角色不存在，返回空列表而不是抛异常——查询接口对
     * 不存在的资源返回空是更宽容的语义，调用方（前端回显）不需要
     * 为“角色刚被删除”这一边界单独处理。
     *
     * @param roleId 角色 ID；为 {@code null} 时返回空列表
     * @return 部门 ID 列表；角色不存在、非自定义范围或无关联时返回空列表
     */
    List<Long> findDepartmentIdsByRoleId(Long roleId);

    /**
     * 全量替换角色自定义数据范围的部门列表。
     *
     * <p>语义是“设置角色的自定义数据范围为这些部门”，而不是逐条增删：
     * 服务端先删除 {@code iam_role_department} 中该角色的所有关联，
     * 再插入新列表。两个步骤在同一事务内完成，任一步失败整体回滚。
     *
     * <p><b>服务端负责：</b>
     * <ol>
     *   <li>校验角色存在</li>
     *   <li>校验角色的 {@code dataScope = 2}（自定义）——非自定义范围下
     *       部门列表无意义，拒绝写入</li>
     *   <li>校验 {@code departmentIds} 中的每个 ID 对应一个未删除的部门
     *       （通过 {@code OrganizationFacade.existsDepartments}）</li>
     *   <li>去重 {@code departmentIds}，避免撞唯一索引</li>
     * </ol>
     *
     * <p><b>空列表是合法的</b>——表示清空自定义范围。此时角色在数据权限
     * 过滤下将查不到任何部门的数据，前端应在角色编辑页提示用户补充范围。
     *
     * <p><b>幂等性：</b>同一请求重复执行的结果与执行一次相同——
     * 先删后插的语义天然幂等。这保证了 Event Publication Registry
     * 重试或前端重复提交时不会产生重复关联。
     *
     * @param roleId        角色 ID
     * @param departmentIds 部门 ID 列表；空列表表示清空自定义范围；
     *                      为 {@code null} 时视为空列表
     * @throws IllegalArgumentException 角色不存在、部门 ID 无效时抛出
     * @throws IllegalStateException    角色数据范围不是自定义时抛出
     */
    void replaceRoleDepartments(Long roleId, List<Long> departmentIds);
}
