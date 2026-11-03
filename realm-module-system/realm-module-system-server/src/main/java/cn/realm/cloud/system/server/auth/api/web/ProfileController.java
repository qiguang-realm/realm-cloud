package cn.realm.cloud.system.server.auth.api.web;

import cn.realm.cloud.framework.security.core.LoginUser;
import cn.realm.cloud.framework.security.core.SecurityUtils;
import cn.realm.cloud.system.server.auth.api.web.dto.ProfileResponse;
import cn.realm.cloud.system.server.auth.api.web.dto.UserMenuNode;
import cn.realm.cloud.system.server.menu.MenuDto;
import cn.realm.cloud.system.server.menu.MenuFacade;
import cn.realm.cloud.system.server.role.RoleFacade;
import cn.realm.cloud.system.server.user.UserDto;
import cn.realm.cloud.system.server.user.UserFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 当前登录用户的资料视图。
 *
 * <p>前端 {@code vue-element-admin} 在登录后调用本端点，获取三样东西：
 * <ul>
 *   <li><b>用户基本信息</b>：昵称、头像，用于顶栏展示</li>
 *   <li><b>权限点列表</b>：{@code system:user:create} 这类字符串，
 *       供前端 {@code v-permission} 指令控制按钮显隐</li>
 *   <li><b>菜单树</b>：已按用户权限过滤、已组装成树，前端直接用于
 *       生成动态路由，不需要二次过滤</li>
 * </ul>
 *
 * <p><b>为什么单独一个端点而不塞进 {@code /auth/login}：</b>登录只需要
 * 签发令牌，不需要查用户资料和菜单树。如果把三者合并到登录响应里，
 * 每次 {@code /auth/refresh} 也得返回同样的内容——而 access token 只有
 * 15 分钟有效期，一天刷新几十次，每次都触发三次数据库查询，浪费明显。
 * 独立端点让“签发令牌”和“组装用户视图”解耦，各自在最合适的时机被调用。
 *
 * <p><b>为什么依赖三个 Facade：</b>菜单树需要 user → role → menu 三段
 * 数据。{@code user} 模块提供“用户的角色 ID”，{@code role} 模块提供
 * “角色授权的菜单 ID”，{@code menu} 模块提供“菜单详情”。三段都通过
 * Facade 访问，不直接触碰任何 {@code internal} 类型。
 *
 * @author QI Guang
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final UserFacade userFacade;
    private final RoleFacade roleFacade;
    private final MenuFacade menuFacade;

    /**
     * 获取当前登录用户的完整视图。
     *
     * <p>返回值包含用户信息、权限点、菜单树。前端 {@code getInfo} action
     * 调用本端点，把结果写入 Vuex 的 {@code user} 和 {@code permission}
     * 两个模块。
     *
     * @return 当前用户的完整视图
     * @throws IllegalStateException 未登录或用户已被删除时抛出
     */
    @GetMapping("/profile")
    public ProfileResponse profile() {
        LoginUser loginUser = SecurityUtils.getLoginUser();

        // 1. 用户基本信息
        UserDto user = userFacade.findById(loginUser.id())
                .orElseThrow(() -> new IllegalStateException(
                        "当前登录用户不存在: " + loginUser.id()));

        // 2. 权限点（user → role → menu → permission_code 的聚合，
        //    由 UserFacade 内部完成，ProfileController 不感知细节）
        Set<String> permissions = userFacade.findPermissionsByUserId(loginUser.id());

        // 3. 菜单树（user → role → menu，由本类编排）
        List<UserMenuNode> menus = buildMenuTree(loginUser.id());

        log.debug("加载用户 profile 成功, userId={}, permissionCount={}, menuCount={}",
                loginUser.id(), permissions.size(), menus.size());

        return new ProfileResponse(
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
