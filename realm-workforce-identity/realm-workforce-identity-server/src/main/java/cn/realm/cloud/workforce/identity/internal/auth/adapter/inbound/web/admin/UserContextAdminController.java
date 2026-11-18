package cn.realm.cloud.workforce.identity.internal.auth.adapter.inbound.web.admin;

import cn.realm.cloud.framework.core.exception.FrameworkErrorCode;
import cn.realm.cloud.framework.core.exception.SystemException;
import cn.realm.cloud.framework.core.principal.PrincipalUtils;
import cn.realm.cloud.framework.core.principal.UserPrincipal;
import cn.realm.cloud.workforce.identity.api.facade.dto.MenuDto;
import cn.realm.cloud.workforce.identity.api.facade.dto.UserDto;
import cn.realm.cloud.workforce.identity.internal.auth.adapter.inbound.web.dto.UserContextResponse;
import cn.realm.cloud.workforce.identity.internal.auth.adapter.inbound.web.dto.UserMenuNode;
import cn.realm.cloud.workforce.identity.internal.menu.MenuFacade;
import cn.realm.cloud.workforce.identity.internal.role.RoleFacade;
import cn.realm.cloud.workforce.identity.internal.user.UserFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Auth domain — Admin view.
 * 认证域【管理后台视图】用户上下文资源入口。
 *
 * <p>面向管理后台的用户上下文视图接口，使用 RBAC 权限模型，依赖已签发的有效令牌访问。
 * 与其他视图（如应用端视图、开放平台视图）在权限模型、DTO契约、可见接口集合上严格隔离。
 *
 * <p>前端 {@code vue-element-admin} 登录后调用该端点，一次性获取三类渲染上下文数据：
 * <ul>
 *   <li><b>用户基本信息</b>：昵称、头像，用于页面顶栏展示</li>
 *   <li><b>权限点列表</b>：形如 {@code system:user:create} 的权限标识，供前端 {@code v-permission} 指令控制按钮显隐</li>
 *   <li><b>菜单树</b>：已按用户权限过滤并组装完成的树形结构，前端直接用于生成动态路由，无需前端二次过滤</li>
 * </ul>
 *
 * <p><b>分层职责边界（Clean Architecture）：</b>
 * <ul>
 *   <li>本 Controller：仅 HTTP 协议适配、OpenAPI 契约声明、权限校验、视图数据编排；</li>
 *   <li>用户、角色、菜单原始数据查询下沉至各域 Facade；</li>
 *   <li>不直接访问其他模块 {@code internal} 内部实现类。</li>
 * </ul>
 *
 * <p>设计说明：独立于 {@code /auth/login} 单独暴露端点。登录动作仅负责签发令牌，不加载用户资料、权限与菜单；
 * 若合并至登录接口，短时效 access token 频繁刷新时会重复触发多表查询，带来不必要性能开销。
 * 拆分后实现令牌签发与用户上下文组装解耦，按需调用。
 *
 * <p>数据编排依赖：菜单视图链路为 user → role → menu，通过 Facade 跨模块协作：
 * user 模块查询用户角色ID集合、role 模块查询角色关联菜单ID集合、menu 模块返回菜单详情。
 * 所有跨域访问均走 Facade 防腐层，不直接依赖其他模块内部实现。
 *
 * <p>业务编排委托：用户、角色、菜单相关 Facade。
 *
 * @author QI Guang
 * @see UserContextResponse
 * @see <a href="https://google.aip.dev/122">AIP-122 Resource names</a>
 * @see <a href="https://google.aip.dev/132">AIP-132 Standard List methods</a>
 */
@RestController
@RequestMapping("/admin/auth")
@Tag(name = "Auth-Admin", description = "认证域｜B端管理后台接口")
@RequiredArgsConstructor
@Slf4j
public class UserContextAdminController {

    private final UserFacade userFacade;
    private final RoleFacade roleFacade;
    private final MenuFacade menuFacade;

    /**
     * 获取当前登录用户上下文视图。
     *
     * <p>返回用户基础信息、权限编码集合、已过滤并组装完成的菜单树。
     * 前端 {@code getInfo} action 调用本端点，结果写入 Vuex {@code user} / {@code permission} 模块。
     *
     * @return 登录用户上下文视图
     * @throws SystemException 未登录、用户记录不存在时抛出
     */
    @Operation(summary = "获取登录用户上下文", description = "包含用户信息、权限标识、动态菜单树，用于前端渲染控制台")
    @GetMapping("/user-context")
    public UserContextResponse getUserContext() {
        // 使用新的安全上下文工具获取用户主体
        UserPrincipal principal = PrincipalUtils.getUserPrincipal();
        Long userId = principal.id();

        // 1. 用户基本信息
        UserDto user = userFacade.findById(userId)
                .orElseThrow(() -> {
                    // Token 有效但查不到用户（如用户已被并发物理删除），属于数据一致性问题
                    log.warn("当前登录用户不存在, userId={}", userId);
                    return SystemException.of(FrameworkErrorCode.DATA_NOT_FOUND,
                            "当前登录用户不存在: " + userId);
                });

        // 2. 权限点（user → role → menu → permission_code 的聚合）
        Set<String> permissions = userFacade.findPermissionsByUserId(userId);

        // 3. 菜单树（user → role → menu，由本类编排）
        List<UserMenuNode> menus = buildMenuTree(userId);

        log.debug("加载用户上下文成功, userId={}, permissionCount={}, menuCount={}",
                userId, permissions.size(), menus.size());

        return new UserContextResponse(
                user.id(),
                user.username(),
                user.nickname(),
                user.avatarUrl(),
                permissions,
                menus
        );
    }

    // ==================== 菜单树编排 ====================

    /**
     * 组装当前用户的菜单树。
     *
     * <p>三步：
     * <ol>
     *   <li>{@code user → roleIds}：{@code UserFacade.findRoleIdsByUserId}
     *       查 {@code iam_user_role}，这张表归 {@code user} 模块所有</li>
     *   <li>{@code roleIds → menuIds}：{@code RoleFacade.findMenuIdsByRoleIds}
     *       查 {@code iam_role_menu}，这张表归 {@code role} 模块所有</li>
     *   <li>{@code menuIds → menuDtos}：{@code MenuFacade.findByIds}
     *       查 {@code iam_menu}，这张表归 {@code menu} 模块所有</li>
     * </ol>
     *
     * <p><b>过滤规则：</b>
     * <ul>
     *   <li>只保留目录（{@code menuType = 1}）和菜单（{@code menuType = 2}），
     *       过滤按钮（{@code menuType = 3}）——按钮没有路由，不应出现在动态路由里</li>
     *   <li>只保留状态正常（{@code status = 0}）的菜单——停用菜单不应渲染</li>
     *   <li>只保留可见（{@code isVisible = 1}）的菜单——隐藏菜单
     *       （如详情页、编辑页）通常通过 {@code hidden: true} 单独配置，
     *       不出现在侧边栏；当前先按 {@code isVisible} 过滤，未来如果需要
     *       “隐藏但可路由”的菜单，再调整</li>
     * </ul>
     *
     * <p><b>孤儿节点处理：</b>如果某个菜单的 {@code parentId} 指向一个
     * 未授权的父菜单（比如用户被授权了子菜单但没授权父目录），该菜单会被
     * 丢弃——不挂在根上，避免出现“孤儿菜单突然出现在侧边栏顶层”的情况。
     * 这是权限配置的问题，应该在角色授权时保证“授权子菜单必须同时授权父目录”，
     * 而不是在这里兜底。
     */
    private List<UserMenuNode> buildMenuTree(Long userId) {
        // 1. 用户 → 角色 ID
        List<Long> roleIds = userFacade.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }

        // 2. 角色 → 菜单 ID
        Set<Long> menuIds = roleFacade.findMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return List.of();
        }

        // 3. 菜单 ID → 菜单详情
        List<MenuDto> menus = menuFacade.findByIds(List.copyOf(menuIds));

        // 4. 过滤：只保留目录和菜单，只保留正常状态，只保留可见项
        List<MenuDto> navigable = menus.stream()
                .filter(m -> m.menuType() != null
                        && (m.menuType() == 1 || m.menuType() == 2))
                .filter(m -> Objects.equals(m.status(), 0))
                .filter(m -> Objects.equals(m.isVisible(), 1))
                .toList();

        // 5. 组树
        return assembleTree(navigable);
    }

    /**
     * 将扁平菜单列表组装成树。
     *
     * <p>按 {@code parentId} 分组，从根（{@code parentId = 0}）开始递归。
     * 每层按 {@code sortOrder} 升序排列，{@code null} 排末尾。
     */
    private List<UserMenuNode> assembleTree(List<MenuDto> menus) {
        Map<Long, List<MenuDto>> byParent = menus.stream()
                .collect(Collectors.groupingBy(
                        m -> m.parentId() == null ? 0L : m.parentId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return buildChildren(0L, byParent);
    }

    /**
     * 递归构造某一层的子节点。
     */
    private List<UserMenuNode> buildChildren(Long parentId,
                                             Map<Long, List<MenuDto>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparing(
                        MenuDto::sortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(m -> new UserMenuNode(
                        m.id(),
                        m.path(),
                        m.component(),
                        m.componentName(),
                        new UserMenuNode.Meta(m.menuName(), m.icon()),
                        buildChildren(m.id(), byParent)
                ))
                .toList();
    }
}
