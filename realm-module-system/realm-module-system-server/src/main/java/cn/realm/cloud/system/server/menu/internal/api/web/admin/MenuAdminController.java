package cn.realm.cloud.system.server.menu.internal.api.web.admin;

import cn.realm.cloud.system.server.menu.internal.api.web.admin.converter.MenuAdminConverter;
import cn.realm.cloud.system.server.menu.internal.api.web.admin.dto.MenuCreateRequest;
import cn.realm.cloud.system.server.menu.internal.api.web.admin.dto.MenuResponse;
import cn.realm.cloud.system.server.menu.internal.api.web.admin.dto.MenuStatusUpdateRequest;
import cn.realm.cloud.system.server.menu.internal.api.web.admin.dto.MenuUpdateRequest;
import cn.realm.cloud.system.server.menu.internal.application.MenuService;
import cn.realm.cloud.system.server.menu.internal.persistence.MenuDO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单模块管理后台接口入口。
 *
 * <p>菜单是 RBAC 中被授权的资源定义，以 {@code parentId} + {@code ancestors}
 * 组织为树形结构。类型 {@code menuType}：1-目录、2-菜单、3-按钮；目录与菜单
 * 通常携带路由和组件路径，按钮只携带权限标识（如 {@code system:user:create}）。
 *
 * <p>本类只做 HTTP 协议适配与权限校验，业务委托给 {@link MenuService}。
 * {@code ancestors} 的拼接、移动节点时子孙链的重算、删除前的子节点校验、
 * 树形结构组装均由应用服务在事务内完成。
 *
 * <p><b>模块依赖提示：</b>删除前的“角色引用校验”不在本模块同步执行——
 * 那会形成 {@code menu → role} 的反向依赖。角色侧的 {@code iam_role_menu}
 * 关联记录由 {@code role} 模块监听 {@code MenuDeletedEvent} 异步清理，
 * 最终一致，具体方向见 ADR 中的模块图。
 *
 * <p>类上的 {@link Validated} 用于让 {@link RequestParam} 上的
 * {@code @Min} / {@code @Max} 生效；{@code @RequestBody} 的校验由
 * {@code @Valid} 触发，两者互补。
 *
 * @author QI Guang
 * @see cn.realm.cloud.system.server.role.internal.api.web.admin.RoleAdminController
 */
@RestController
@RequestMapping("/admin/iam/menu")
@RequiredArgsConstructor
@Validated
public class MenuAdminController {

    private final MenuService menuService;

    /* ==================== 菜单 CRUD ==================== */

    /**
     * 创建菜单。
     *
     * <p>{@code ancestors} 由服务端根据 {@code parentId} 计算，请求中不需要携带。
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:menu:create')")
    public Long create(@Valid @RequestBody MenuCreateRequest request) {
        return menuService.createMenu(MenuAdminConverter.fromCreateRequest(request));
    }

    /**
     * 更新菜单。
     *
     * <p>不支持通过本端点修改 {@code parentId} 和 {@code menuType}——
     * 移动节点会触发整棵子树的 {@code ancestors} 重算，由独立的 move 端点处理；
     * {@code menuType} 是结构性属性，创建后变更需要额外的关联校验。
     * 服务层会用数据库中的原值覆盖这两个字段，双重保护。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:menu:update')")
    public void update(@PathVariable Long id, @Valid @RequestBody MenuUpdateRequest request) {
        MenuDO menu = menuService.getMenu(id);
        MenuAdminConverter.applyUpdateRequest(request, menu);
        menuService.updateMenu(menu);
    }

    /**
     * 查询菜单详情（不含子节点）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:menu:query')")
    public MenuResponse get(@PathVariable Long id) {
        return MenuAdminConverter.toResponse(menuService.getMenu(id));
    }

    /**
     * 查询菜单树（不分页），供菜单管理页、角色分配菜单页使用。
     *
     * <p>树组装在 {@link MenuService#listTree(Integer)} 内完成，
     * Controller 直接透传服务层结果，不再经过 Converter 或手动组树。
     *
     * @param type 可选的菜单类型过滤：1-目录、2-菜单、3-按钮；
     *             不传时返回完整树。角色分配菜单时通常传 {@code 2}，
     *             只展示可授权的菜单节点，不包含目录和按钮
     */
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPermission('system:menu:query')")
    public List<MenuResponse> tree(
            @RequestParam(required = false) @Min(1) @Max(3) Integer type) {
        return menuService.listTree(type);
    }

    /**
     * 变更菜单状态（启用 / 停用）。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermission('system:menu:update')")
    public void changeStatus(@PathVariable Long id,
                             @Valid @RequestBody MenuStatusUpdateRequest request) {
        menuService.changeStatus(id, request.status());
    }

    /**
     * 删除菜单。
     *
     * <p>服务端在删除前会校验是否存在未删除的子菜单，存在时拒绝删除，
     * 避免留下孤儿节点。被角色引用的关联记录由 {@code role} 模块监听
     * {@code MenuDeletedEvent} 异步清理，不在本端点同步校验。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:menu:delete')")
    public void delete(@PathVariable Long id) {
        menuService.deleteMenu(id);
    }
}
