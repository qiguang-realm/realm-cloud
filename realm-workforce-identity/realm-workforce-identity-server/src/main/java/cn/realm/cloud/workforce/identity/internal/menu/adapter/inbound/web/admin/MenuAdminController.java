package cn.realm.cloud.workforce.identity.internal.menu.adapter.inbound.web.admin;

import cn.realm.cloud.workforce.identity.internal.menu.adapter.inbound.web.admin.converter.MenuAdminConverter;
import cn.realm.cloud.workforce.identity.internal.menu.adapter.inbound.web.admin.dto.MenuCreateRequest;
import cn.realm.cloud.workforce.identity.internal.menu.adapter.inbound.web.admin.dto.MenuResponse;
import cn.realm.cloud.workforce.identity.internal.menu.adapter.inbound.web.admin.dto.MenuStatusUpdateRequest;
import cn.realm.cloud.workforce.identity.internal.menu.adapter.inbound.web.admin.dto.MenuUpdateRequest;
import cn.realm.cloud.workforce.identity.internal.menu.application.MenuService;
import cn.realm.cloud.workforce.identity.internal.menu.infrastructure.persistence.MenuDO;
import cn.realm.cloud.workforce.identity.internal.role.adapter.inbound.web.admin.RoleAdminController;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Identity domain — Admin view.
 * 身份域【管理后台视图】菜单资源入口。
 *
 * <p>面向管理后台的菜单管理接口，使用 RBAC 权限模型
 * （如 {@code @PreAuthorize("@ss.hasPermission('iam:menu:create')")}），
 * 提供菜单的树形查询、创建、修改、状态变更、逻辑删除等管理能力。
 * 与其他视图（如应用端视图、开放平台视图）在权限模型、DTO契约、可见接口集合上严格隔离。
 *
 * <p>菜单是 RBAC 授权资源定义，依托 {@code parentId} + {@code ancestors} 维护树形层级；
 * 菜单类型 {@code menuType}：1-目录、2-菜单、3-按钮；目录与菜单携带路由、组件路径，
 * 按钮仅绑定权限标识（如 {@code system:user:create}）。对应存储表：{@code iam_menu}。
 *
 * <p><b>分层职责边界（Clean Architecture）：</b>
 * <ul>
 *   <li>本 Controller：仅 HTTP 协议适配、OpenAPI 契约声明、权限注解校验、参数格式校验；</li>
 *   <li>业务规则校验（父节点合法性、节点移动、祖先链重算、子节点校验、树形组装）下沉至 Application Service；</li>
 *   <li>不包含任何业务判断、领域逻辑。</li>
 * </ul>
 *
 * <p>删除语义：删除前不做角色引用同步校验，避免 {@code menu → role} 反向依赖。
 * 角色侧 {@code iam_role_menu} 关联数据由 {@code role} 模块监听 {@code MenuDeletedEvent} 异步清理，保障最终一致性，
 * 依赖方向参考 ADR 模块图。
 *
 * <p>校验说明：类上 {@link Validated} 用于支持 {@link RequestParam} 参数上的 {@code @Min}/{@code @Max}；
 * {@code @RequestBody} 请求体校验由方法上 {@code @Valid} 触发，二者互补。
 *
 * <p>业务编排委托：{@link MenuService}。
 *
 * @author QI Guang
 * @see RoleAdminController
 * @see <a href="https://google.aip.dev/122">AIP-122 Resource names</a>
 * @see <a href="https://google.aip.dev/132">AIP-132 Standard List methods</a>
 */
@RestController
@RequestMapping("/admin/iam/menu")
@Tag(name = "Identity-Admin", description = "身份域｜B端管理后台接口")
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
