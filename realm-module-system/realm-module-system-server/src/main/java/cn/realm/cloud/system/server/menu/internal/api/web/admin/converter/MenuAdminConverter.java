package cn.realm.cloud.system.server.menu.internal.api.web.admin.converter;

import cn.realm.cloud.system.server.menu.internal.api.web.admin.dto.MenuCreateRequest;
import cn.realm.cloud.system.server.menu.internal.api.web.admin.dto.MenuResponse;
import cn.realm.cloud.system.server.menu.internal.api.web.admin.dto.MenuUpdateRequest;
import cn.realm.cloud.system.server.menu.internal.persistence.MenuDO;

import java.util.List;

/**
 * 管理后台 Request/Response 与 {@link MenuDO} 的转换。
 *
 * <p>只服务于 {@code internal.api.web.admin} 下的 Controller，
 * 不跨端复用——App 端若需要菜单，应使用独立的窄 Response，
 * 避免把管理字段（如权限标识、可见性配置）暴露给 C 端。
 *
 * <p><b>职责边界：</b>本类只做<b>字段映射</b>，不负责树组装、
 * 不负责 {@code ancestors} 计算、不负责排序。树结构由
 * {@link cn.realm.cloud.system.server.menu.internal.application.MenuService#listTree(Integer)}
 * 组装后返回，本类只提供逐节点的映射方法
 * {@link #toResponse(MenuDO)} 与 {@link #toResponse(MenuDO, List)}，
 * 供 Service 自底向上构造树时调用。
 *
 * <p><b>不映射的字段：</b>{@code ancestors}（服务端子树查询辅助列）、
 * {@code activePermissionCode}（逻辑删除唯一约束辅助列）、
 * {@code tenantId}（租户上下文注入）、{@code createBy} / {@code updateBy} /
 * {@code version} / {@code isDeleted}（审计与并发控制）。
 *
 * @author QI Guang
 */
public final class MenuAdminConverter {

    private MenuAdminConverter() {
    }

    // ==================== 入站：Request → DO ====================

    /**
     * 创建请求 → 新 {@link MenuDO}。
     *
     * <p>只映射请求中允许客户端提供的字段。{@code ancestors} 由服务端
     * 根据 {@code parentId} 计算，{@code createTime}、{@code createBy}
     * 等审计字段由持久化层或服务层补齐，均不在本方法中设置。
     */
    public static MenuDO fromCreateRequest(MenuCreateRequest request) {
        if (request == null) {
            return null;
        }
        MenuDO menu = new MenuDO();
        menu.setParentId(request.parentId());
        menu.setMenuName(request.menuName());
        menu.setMenuType(request.menuType());
        menu.setSortOrder(request.sortOrder());
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setComponentName(request.componentName());
        menu.setPermissionCode(request.permissionCode());
        menu.setIcon(request.icon());
        menu.setIsVisible(request.isVisible());
        menu.setIsKeepAlive(request.isKeepAlive());
        menu.setIsAlwaysShow(request.isAlwaysShow());
        menu.setStatus(request.status());
        return menu;
    }

    /**
     * 更新请求 → 应用到已有 {@link MenuDO}。
     *
     * <p>不包含 {@code parentId}、{@code menuType}、{@code status}：
     * 前两者由独立端点或结构性规则处理，后者走独立的状态变更端点，
     * 避免“编辑菜单时顺便改状态”绕过状态变更的独立语义。
     */
    public static void applyUpdateRequest(MenuUpdateRequest request, MenuDO menu) {
        if (request == null || menu == null) {
            return;
        }
        menu.setMenuName(request.menuName());
        menu.setSortOrder(request.sortOrder());
        menu.setPath(request.path());
        menu.setComponent(request.component());
        menu.setComponentName(request.componentName());
        menu.setPermissionCode(request.permissionCode());
        menu.setIcon(request.icon());
        menu.setIsVisible(request.isVisible());
        menu.setIsKeepAlive(request.isKeepAlive());
        menu.setIsAlwaysShow(request.isAlwaysShow());
    }

    // ==================== 出站：DO → Response ====================

    /**
     * {@link MenuDO} → 详情响应（不含子节点）。
     *
     * <p>等价于 {@code toResponse(menu, List.of())}，供详情接口使用。
     */
    public static MenuResponse toResponse(MenuDO menu) {
        return toResponse(menu, List.of());
    }

    /**
     * {@link MenuDO} → 树节点响应，携带已组装好的子节点。
     *
     * <p>供 {@code MenuService.listTree} 自底向上组装树时逐层调用：
     * 先构造叶子节点的 {@code MenuResponse}（{@code children} 为空列表），
     * 再构造父节点时把子节点列表传入。由于 {@code MenuResponse} 是
     * 不可变 {@code record}，不能用“先建父节点再塞子节点”的命令式写法，
     * 必须自底向上构造。
     *
     * @param menu     菜单持久化对象
     * @param children 已组装好的子节点列表；{@code null} 时由
     *                 {@code MenuResponse} 的紧凑构造器归一为空列表
     */
    public static MenuResponse toResponse(MenuDO menu, List<MenuResponse> children) {
        if (menu == null) {
            return null;
        }
        return new MenuResponse(
                menu.getId(),
                menu.getParentId(),
                menu.getMenuName(),
                menu.getMenuType(),
                menu.getSortOrder(),
                menu.getPath(),
                menu.getComponent(),
                menu.getComponentName(),
                menu.getPermissionCode(),
                menu.getIcon(),
                menu.getIsVisible(),
                menu.getIsKeepAlive(),
                menu.getIsAlwaysShow(),
                menu.getStatus(),
                menu.getCreateTime(),
                children
        );
    }

    /**
     * 扁平列表映射：{@code List<MenuDO>} → {@code List<MenuResponse>}（不含子节点）。
     *
     * <p>只用于确实需要“扁平 Response 列表”的场景；树形查询请直接用
     * {@code MenuService.listTree}，它返回的已经是组装好的树。
     */
    public static List<MenuResponse> toResponseList(List<MenuDO> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        return menus.stream()
                .map(MenuAdminConverter::toResponse)
                .toList();
    }
}
