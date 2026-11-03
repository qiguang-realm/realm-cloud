package cn.realm.cloud.system.server.menu;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 菜单模块的跨模块门面。
 *
 * <p>本接口是 {@code menu} 模块对外的<b>只读</b>能力边界。其他模块
 * （如 {@code role} 在分配角色菜单授权前校验菜单有效性、
 * {@code user} 在登录时聚合用户权限点）通过注入本接口获取菜单信息，
 * <b>不</b>直接引用 {@code menu.internal} 中的 {@code MenuService}、
 * {@code MenuRepository} 或 {@code MenuDO}。Spring Modulith 的
 * {@code verify()} 会阻止跨模块引用 {@code internal} 类型。
 *
 * <p><b>为什么只有只读方法：</b>菜单的创建、更新、状态变更、删除是由
 * HTTP 适配器（{@code MenuAdminController}）触发的用例，跨模块不应
 * 绕过用例层直接写菜单数据。其他模块需要"菜单发生了某事"时，应订阅
 * {@code menu.events} 下的事件（如 {@code MenuDeletedEvent}），而不是
 * 调用写方法。
 *
 * <p><b>与 {@code UserFacade} / {@code OrganizationFacade} / {@code RoleFacade}
 * 的差异：</b>前三者都是四个方法（{@code existsById} / {@code existsByIds}
 * / {@code findById} / {@code findByIds}）。{@code MenuFacade} 多一个
 * {@link #findPermissionCodesByMenuIds(Collection)}——因为菜单是权限点的
 * 载体（{@code iam_menu.permission_code}），登录时的权限点聚合需要
 * 这一步。这是业务语义差异，不是结构不一致。
 *
 * @author QI Guang
 */
public interface MenuFacade {

    /**
     * 判断菜单是否存在（未删除）。
     *
     * @param id 菜单 ID；为 {@code null} 时返回 {@code false}
     * @return 存在未删除的菜单时为 {@code true}
     */
    boolean existsById(Long id);

    /**
     * 批量判断菜单是否全部存在（未删除）。
     *
     * <p>供 {@code role} 模块在分配角色菜单授权前做校验——这是
     * {@code role → menu} 的同步依赖：{@code RoleMenuService.replaceRoleMenus}
     * 在写入 {@code iam_role_menu} 前，通过本方法确认所有 {@code menuIds}
     * 都对应未删除的菜单，避免把无效 ID 写入关联表。
     *
     * <p>返回 {@code true} 当且仅当 {@code ids} 中的每个 ID 都对应
     * 一个未删除的菜单。{@code ids} 为空时返回 {@code true}
     * （空集合视为"无需校验"）。
     *
     * @param ids 菜单 ID 列表；为 {@code null} 或空集合时返回 {@code true}
     * @return 全部存在时为 {@code true}，任一不存在时为 {@code false}
     */
    boolean existsByIds(List<Long> ids);

    /**
     * 按 ID 查询菜单只读信息。
     *
     * @param id 菜单 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除菜单的只读信息；不存在或已逻辑删除时返回空
     */
    Optional<MenuDto> findById(Long id);

    /**
     * 按主键批量查询菜单只读信息。
     *
     * @param ids 菜单 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除菜单的只读信息列表，顺序不保证与入参一致
     */
    List<MenuDto> findByIds(List<Long> ids);

    /**
     * 批量查询菜单的权限点编码集合。
     *
     * <p>供 {@code UserFacade.findPermissionsByUserId} 使用：用户 → 角色
     * → 菜单 → 权限点，这是登录时聚合权限点链路的最后一步。
     *
     * <p><b>过滤规则：</b>只返回满足以下条件的菜单的 {@code permission_code}：
     * <ul>
     *   <li>菜单未删除（{@code is_deleted = 0}）</li>
     *   <li>菜单状态为正常（{@code status = 0}）</li>
     *   <li>{@code permission_code} 非 {@code null} 且非空白——目录节点
     *       通常没有权限标识，不应贡献空字符串</li>
     * </ul>
     *
     * <p><b>返回 {@code Set} 而非 {@code List}：</b>权限点编码在菜单间
     * 全局唯一（由 {@code uk_iam_menu_tenant_active_permission_code} 保证），
     * 但同一用户可能通过多个角色授权了不同菜单、这些菜单可能对应同一个
     * 权限点（虽然设计上不该出现，但防御性去重成本极低）。{@code Set}
     * 天然去重，且返回不可变集合防止调用方意外修改。
     *
     * <p><b>性能说明：</b>本方法在登录热路径上被调用（每个请求的
     * {@code TokenAuthenticationFilter} 都可能触发）。每次调用是一次
     * {@code IN} 查询，菜单数量通常几十到几百，开销可接受。
     * 生产环境应在 {@code SystemLoginUserProvider.loadUserById} 层按
     * {@code userId} 缓存 {@code LoginUser}（含 {@code permissions}），
     * 而不是在本方法内部缓存——缓存粒度按用户比按菜单集合更合理。
     *
     * @param menuIds 菜单 ID 集合；为 {@code null} 或空集合时返回空集合
     * @return 权限点编码集合；无有效权限点时返回空集合
     */
    Set<String> findPermissionCodesByMenuIds(Collection<Long> menuIds);
}
