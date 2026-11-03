package cn.realm.cloud.system.server.role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 角色模块的跨模块门面。
 *
 * <p>本接口是 {@code role} 模块对外的<b>只读</b>能力边界。其他模块
 * （如 {@code user} 在分配用户角色前校验角色有效性、在用户详情中
 * 展示角色名称、在登录时聚合权限点）通过注入本接口获取角色信息，
 * <b>不</b>直接引用 {@code role.internal} 中的 {@code RoleService}、
 * {@code RoleRepository} 或 {@code RoleDO}。Spring Modulith 的
 * {@code verify()} 会阻止跨模块引用 {@code internal} 类型。
 *
 * <p><b>为什么只有只读方法：</b>角色的创建、更新、状态变更、删除是由
 * HTTP 适配器（{@code RoleAdminController}）触发的用例，跨模块不应
 * 绕过用例层直接写角色数据。其他模块需要"角色发生了某事"时，应订阅
 * {@code role.events} 下的事件（如 {@code RoleDeletedEvent}），
 * 而不是调用写方法。
 *
 * <p><b>返回类型：</b>所有方法返回 {@link RoleDto} 或 {@code boolean} /
 * {@code Set} 等中性类型，不暴露 {@link cn.realm.cloud.system.server.role.internal.persistence.RoleDO}。
 * {@code RoleDto} 是模块公开契约的一部分，字段变更需要评估对其他模块的影响。
 *
 * <p><b>实现类：</b>{@code RoleFacadeImpl} 位于 {@code internal} 包，
 * 由 {@code verify()} 保护，其他模块无法直接引用。
 *
 * @author QI Guang
 */
public interface RoleFacade {

    /**
     * 判断角色是否存在（未删除）。
     *
     * @param id 角色 ID；为 {@code null} 时返回 {@code false}
     * @return 存在未删除的角色时为 {@code true}
     */
    boolean existsById(Long id);

    /**
     * 批量判断角色是否全部存在（未删除）。
     *
     * <p>供 {@code user} 模块在分配用户角色前做校验——这是
     * {@code user → role} 的同步依赖：{@code UserRoleService.replaceUserRoles}
     * 在写入 {@code iam_user_role} 前，通过本方法确认所有 {@code roleIds}
     * 都对应未删除的角色，避免把无效 ID 写入关联表。
     *
     * <p>返回 {@code true} 当且仅当 {@code ids} 中的每个 ID 都对应
     * 一个未删除的角色。{@code ids} 为空时返回 {@code true}
     * （空集合视为"无需校验"）。
     *
     * @param ids 角色 ID 列表；为 {@code null} 或空集合时返回 {@code true}
     * @return 全部存在时为 {@code true}，任一不存在时为 {@code false}
     */
    boolean existsByIds(List<Long> ids);

    /**
     * 按 ID 查询角色只读信息。
     *
     * <p>供其他模块读取角色的基本信息（编码、名称、状态），用于展示
     * 或业务判断。返回的是 {@link RoleDto}，不包含审计字段、逻辑删除
     * 标记、{@code activeRoleCode} / {@code activeRoleName} 等内部字段。
     *
     * @param id 角色 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除角色的只读信息；不存在或已逻辑删除时返回空
     */
    Optional<RoleDto> findById(Long id);

    /**
     * 按主键批量查询角色只读信息。
     *
     * <p>供 {@code user} 模块在用户详情接口中批量查询角色名称列表——
     * 前端展示"该用户拥有哪些角色"时，需要角色的 {@code roleName}，
     * 而不是只有 {@code roleId}。
     *
     * @param ids 角色 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除角色的只读信息列表，顺序不保证与入参一致
     */
    List<RoleDto> findByIds(List<Long> ids);

    /**
     * 批量查询角色授权的菜单 ID 集合。
     *
     * <p>供 {@code UserFacade.findPermissionsByUserId} 使用：用户 → 角色 → 菜单
     * → 权限点，这是登录时聚合权限点链路的第二步。给定一批角色 ID，
     * 返回这些角色通过 {@code iam_role_menu} 授权的所有菜单 ID 的<b>并集</b>
     * （同一菜单被多个角色授权时只出现一次）。
     *
     * <p><b>为什么在 {@code role} 模块而非 {@code menu}：</b>
     * {@code iam_role_menu} 归 {@code role} 模块所有，本方法只查这张表，
     * 不跨模块。{@code menu} 模块负责把菜单 ID 映射为权限点编码
     * （{@code MenuFacade.findPermissionCodesByMenuIds}），职责清晰。
     *
     * <p><b>返回空集合的场景：</b>
     * <ul>
     *   <li>{@code roleIds} 为 {@code null} 或空集合</li>
     *   <li>给定角色均未授权任何菜单</li>
     *   <li>给定角色均已删除（本方法不校验角色是否存在——调用方
     *       {@code UserFacade} 从 {@code iam_user_role} 拿到的 roleIds
     *       理论上都是有效角色，但即使有悬空 ID，查不到关联时也只是返回空集合，
     *       不会报错）</li>
     * </ul>
     *
     * <p><b>性能：</b>一次 {@code IN} 查询，{@code roleIds} 的数量上限通常
     * 是单个用户拥有角色的数量（几十个以内），查询开销可忽略。生产环境
     * 建议在 {@code UserFacade.findPermissionsByUserId} 层做缓存，
     * 而不是在本方法内部缓存——缓存粒度按用户而非按角色集合更合理。
     *
     * @param roleIds 角色 ID 集合；为 {@code null} 或空集合时返回空集合
     * @return 菜单 ID 集合；无授权时返回空集合
     */
    Set<Long> findMenuIdsByRoleIds(Collection<Long> roleIds);
}
