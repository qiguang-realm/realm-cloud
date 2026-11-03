package cn.realm.cloud.system.server.role.internal.application;

import cn.realm.cloud.system.server.menu.MenuFacade;
import cn.realm.cloud.system.server.role.internal.persistence.RoleMenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * {@link RoleMenuService} 的默认实现。
 *
 * <p>数据访问委托给 {@link RoleMenuRepository}，菜单存在性校验
 * 委托给 {@link MenuFacade}。本类不直接依赖 MyBatis-Plus，
 * 也不直接查询 {@code iam_menu} 表——那会越过 {@code menu} 模块的
 * 数据所有权。
 *
 * <p><b>跨模块依赖方向：</b>本类通过 {@code MenuFacade.existsByIds}
 * <b>同步</b>校验菜单有效性（{@code role → menu}），{@code menu} 模块
 * 通过发布 {@code MenuDeletedEvent} 通知关联清理
 * （{@code menu → events → role}）。两个方向同时存在不构成循环——
 * {@code menu} 不反向同步依赖 {@code role} 的内部类型，也不查询
 * {@code iam_role_menu} 表。
 *
 * <p><b>与 {@link RoleDepartmentServiceImpl} 的差异：</b>本类<b>不</b>校验
 * 角色的 {@code dataScope}——菜单授权与数据范围无关。角色无论处于哪种
 * 数据范围，都可以拥有菜单授权。
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class RoleMenuServiceImpl implements RoleMenuService {

    private final RoleService roleService;
    private final RoleMenuRepository roleMenuRepository;
    private final MenuFacade menuFacade;

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>先校验角色存在。角色不存在时返回空列表，
     * 而不是抛异常——查询接口对“不存在的关联”返回空，这与
     * {@code replaceRoleMenus} 的严格校验不同：读操作宽容，写操作严格。
     *
     * <p>与 {@code RoleDepartmentServiceImpl.findDepartmentIdsByRoleId}
     * 的差异：那里还需要额外校验 {@code dataScope = 2}，本方法不需要。
     */
    @Override
    public List<Long> findMenuIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        if (roleService.findById(roleId).isEmpty()) {
            return List.of();
        }
        return roleMenuRepository.findMenuIdsByRoleId(roleId);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>直接委托给 {@link RoleMenuRepository#findMenuIdsByRoleIds}，
     * 一次 {@code IN} 查询完成。不校验角色是否存在——调用方从
     * {@code iam_user_role} 拿到的 {@code roleIds} 理论上都是有效角色，
     * 即使有悬空 ID，查不到关联时也只是返回空集合，不会报错。
     *
     * <p><b>为什么不做额外的角色存在性校验：</b>
     * <ol>
     *   <li>这是登录流程的热路径（每个请求都可能触发），多一次角色存在性
     *       查询会显著增加开销</li>
     *   <li>悬空 roleId 的正确处理方式是返回该角色对应的空授权，
     *       而不是抛异常——单个悬空 ID 不应阻断整个用户的权限点计算</li>
     *   <li>如果业务需要“发现悬空 roleId 时告警”，应该在数据清理侧
     *       （{@code RoleDeletedEvent} 的消费者）处理，而不是在查询路径上</li>
     * </ol>
     */
    @Override
    public Set<Long> findMenuIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return roleMenuRepository.findMenuIdsByRoleIds(roleIds);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现步骤：</b>
     * <ol>
     *   <li>校验角色存在（不存在时 {@code getRole} 抛异常）</li>
     *   <li>去重 {@code menuIds}，避免撞唯一索引</li>
     *   <li>校验菜单 ID 有效性（通过 {@code MenuFacade.existsByIds}）</li>
     *   <li>全量替换：先删后插，同一事务内完成</li>
     * </ol>
     *
     * <p><b>校验顺序的理由：</b>先校验角色（最轻的本地查询），
     * 再校验菜单 ID（跨模块调用，最重的操作），最后写入。这样无效请求
     * 尽早失败，不浪费一次跨模块查询。
     */
    @Override
    @Transactional
    public void replaceRoleMenus(Long roleId, List<Long> menuIds) {
        // 1. 校验角色存在
        roleService.getRole(roleId);

        // 2. 去重；null 视为空列表
        List<Long> distinctIds = (menuIds == null || menuIds.isEmpty())
                ? List.of()
                : menuIds.stream().distinct().toList();

        // 3. 校验菜单有效性——空列表跳过（清空场景无需校验）
        if (!distinctIds.isEmpty() && !menuFacade.existsByIds(distinctIds)) {
            throw new IllegalArgumentException(
                    "菜单 ID 列表中存在无效菜单: " + distinctIds);
        }

        // 4. 全量替换
        roleMenuRepository.replaceByRoleId(roleId, distinctIds);
        log.info("替换角色菜单授权, roleId={}, menuCount={}",
                roleId, distinctIds.size());
    }
}
