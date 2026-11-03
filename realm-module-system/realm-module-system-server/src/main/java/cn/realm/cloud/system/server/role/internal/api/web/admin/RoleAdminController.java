package cn.realm.cloud.system.server.role.internal.api.web.admin;

import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.role.internal.api.web.admin.converter.RoleAdminConverter;
import cn.realm.cloud.system.server.role.internal.api.web.admin.dto.*;
import cn.realm.cloud.system.server.role.internal.application.RoleDepartmentService;
import cn.realm.cloud.system.server.role.internal.application.RoleMenuService;
import cn.realm.cloud.system.server.role.internal.application.RoleService;
import cn.realm.cloud.system.server.role.internal.persistence.RoleDO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色模块管理后台接口入口。
 *
 * <p>角色是 RBAC 中被授权的客体，拥有 {@code dataScope} 数据范围配置。
 * 本类除角色的 CRUD 外，还承载两类关联的分配端点：
 * <ul>
 *   <li>角色-菜单：{@code /{roleId}/menus}，全量替换角色可访问的菜单</li>
 *   <li>角色-部门：{@code /{roleId}/departments}，全量替换 {@code dataScope = 2}
 *       （自定义）时可访问的部门范围</li>
 * </ul>
 * 这两类关联表（{@code iam_role_menu}、{@code iam_role_department}）没有独立于
 * 角色的生命周期，因此不暴露为独立资源，而是作为角色编辑的子能力挂在角色资源下。
 *
 * <p>本类只做 HTTP 协议适配与权限校验，业务委托给 {@link RoleService}、
 * {@link RoleMenuService}、{@link RoleDepartmentService}。
 *
 * <p><b>校验职责：</b>{@code @Valid} 触发 DTO 上的格式校验
 * （{@code @NotBlank} / {@code @Min} / {@code @Max}），业务规则校验
 * （如 {@code pageSize} 范围、角色名称唯一性、菜单/部门 ID 有效性）
 * 由 Service 层完成——本类只做 HTTP 协议适配与权限校验，不做业务判断。
 *
 * <p><b>模块依赖提示：</b>菜单/部门 ID 的有效性校验通过 {@code MenuFacade}
 * 和 {@code OrganizationFacade} 同步完成（{@code role → menu}、
 * {@code role → organization}）；菜单/部门删除后的关联清理通过订阅
 * {@code menu::events} / {@code organization::events} 异步完成
 * （{@code role → menu::events}、{@code role → organization::events}）。
 * 两个方向同时存在不构成循环，但必须在 {@code role/package-info.java}
 * 的 {@code allowedDependencies} 中显式声明。
 *
 * @author QI Guang
 */
@RestController
@RequestMapping("/admin/iam/role")
@RequiredArgsConstructor
public class RoleAdminController {

    private final RoleService roleService;
    private final RoleMenuService roleMenuService;
    private final RoleDepartmentService roleDepartmentService;

    /* ==================== 角色 CRUD ==================== */

    /**
     * 创建角色。
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:role:create')")
    public Long create(@Valid @RequestBody RoleCreateRequest request) {
        return roleService.createRole(RoleAdminConverter.fromCreateRequest(request));
    }

    /**
     * 更新角色。
     *
     * <p>不支持通过本端点修改 {@code roleCode}——它是业务标识，被
     * {@code iam_user_role} 等关联表引用，变更会导致追溯困难。
     * 服务层会用数据库中的原值覆盖，Repository 层也不 set 该字段，
     * 形成双重保护。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:role:update')")
    public void update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        RoleDO role = roleService.getRole(id);
        RoleAdminConverter.applyUpdateRequest(request, role);
        roleService.updateRole(role);
    }

    /**
     * 查询角色详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public RoleResponse get(@PathVariable Long id) {
        return RoleAdminConverter.toResponse(roleService.getRole(id));
    }

    /**
     * 分页查询角色。
     *
     * <p>{@code RolePageRequest} 为 record，作为 GET 查询参数绑定；
     * {@code @ParameterObject} 让 springdoc 按字段展开文档，而非把整个
     * record 渲染成一个请求体。若项目未引入 springdoc，可去掉该注解，
     * 绑定行为不变，仅文档退化。
     *
     * <p><b>参数传递：</b>{@code request.toPageParam()} 把分页部分剥离为
     * 框架层的 {@code PageParam}，{@code request.roleName()} /
     * {@code request.status()} 作为查询条件独立传递。Service 不感知
     * HTTP 契约，只接收框架参数 + 业务参数——与
     * {@code UserAdminController.page} / {@code PositionAdminController.page}
     * 保持一致。
     */
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public PageResult<RoleResponse> page(@Valid @ParameterObject RolePageRequest request) {
        return RoleAdminConverter.toPageResponse(roleService.page(
                request.toPageParam(), request.roleName(), request.status()));
    }

    /**
     * 变更角色状态（启用 / 停用）。
     *
     * <p>角色停用后，持有该角色的用户在权限校验时会被拒绝——
     * 由 {@code RoleService.changeStatus} 发布事件，认证/权限模块监听后处理。
     * 状态未实际变化时 Service 层会短路，不写库、不发事件。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermission('system:role:update')")
    public void changeStatus(@PathVariable Long id,
                             @Valid @RequestBody RoleStatusUpdateRequest request) {
        roleService.changeStatus(id, request.status());
    }

    /**
     * 删除角色。
     *
     * <p>服务端在删除后发布 {@code RoleDeletedEvent}，
     * {@code user} 模块监听并清理 {@code iam_user_role} 中的残留关联。
     * 本模块不反向查询 {@code iam_user_role}——那会与
     * {@code user → role::events} 形成循环。
     *
     * <p>同模块的两个关联表（{@code iam_role_menu}、{@code iam_role_department}）
     * 由 {@code RoleServiceImpl.deleteRole} 在同一事务内直接清理，
     * 不依赖事件机制。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:role:delete')")
    public void delete(@PathVariable Long id) {
        roleService.deleteRole(id);
    }

    /* ==================== 角色-菜单分配 ==================== */

    /**
     * 查询角色已分配的菜单 ID 列表。
     *
     * <p>供角色编辑页回显勾选状态；前端再结合 {@code MenuAdminController.tree}
     * 拿到完整菜单树，渲染带勾选的树形控件。
     *
     * <p>角色不存在时返回空列表，而不是抛异常——查询接口对不存在的资源
     * 返回空是更宽容的语义，前端不需要为“角色刚被删除”这一边界单独处理。
     */
    @GetMapping("/{roleId}/menus")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public List<Long> getRoleMenus(@PathVariable Long roleId) {
        return roleMenuService.findMenuIdsByRoleId(roleId);
    }

    /**
     * 全量替换角色的菜单分配。
     *
     * <p>语义是“设置角色拥有这些菜单”，而不是逐条增删。服务端在写入前通过
     * {@code MenuFacade.existsByIds} 校验 {@code menuIds} 是否全部有效，
     * 避免把无效 ID 写入 {@code iam_role_menu}。
     *
     * <p>空列表是合法的——表示清空该角色的所有菜单授权。
     * {@code RoleMenuAssignRequest} 的 {@code menuIds} 用 {@code @NotNull}
     * 而非 {@code @NotEmpty}，正是为了允许“清空”这一语义。
     */
    @PutMapping("/{roleId}/menus")
    @PreAuthorize("@ss.hasPermission('system:role:assign-menu')")
    public void assignRoleMenus(@PathVariable Long roleId,
                                @Valid @RequestBody RoleMenuAssignRequest request) {
        roleMenuService.replaceRoleMenus(roleId, request.menuIds());
    }

    /* ==================== 角色-部门数据范围 ==================== */

    /**
     * 查询角色自定义数据范围的部门 ID 列表。
     *
     * <p>仅当角色 {@code dataScope = 2}（自定义）时返回非空列表；
     * 其他数据范围（全部、本部门、本部门及以下、仅本人）下返回空列表
     * ——不是抛异常，而是返回空，因为“非自定义范围没有部门列表”
     * 是合法状态，不是错误。
     */
    @GetMapping("/{roleId}/departments")
    @PreAuthorize("@ss.hasPermission('system:role:query')")
    public List<Long> getRoleDepartments(@PathVariable Long roleId) {
        return roleDepartmentService.findDepartmentIdsByRoleId(roleId);
    }

    /**
     * 全量替换角色自定义数据范围的部门列表。
     *
     * <p>服务端在写入前校验两件事：
     * <ul>
     *   <li>角色的 {@code dataScope} 必须为 {@code 2}（自定义），否则拒绝——
     *       非自定义范围下这张表的数据无意义</li>
     *   <li>{@code departmentIds} 通过 {@code OrganizationFacade.existsByIds}
     *       校验是否全部有效</li>
     * </ul>
     *
     * <p>空列表是合法的——表示清空自定义范围。此时角色在数据权限过滤下
     * 将查不到任何部门的数据，前端应在角色编辑页提示用户补充范围。
     */
    @PutMapping("/{roleId}/departments")
    @PreAuthorize("@ss.hasPermission('system:role:assign-data-scope')")
    public void assignRoleDepartments(
            @PathVariable Long roleId,
            @Valid @RequestBody RoleDepartmentAssignRequest request) {
        roleDepartmentService.replaceRoleDepartments(roleId, request.departmentIds());
    }
}
