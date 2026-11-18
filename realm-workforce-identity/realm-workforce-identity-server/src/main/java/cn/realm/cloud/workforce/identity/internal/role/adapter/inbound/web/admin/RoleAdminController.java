package cn.realm.cloud.workforce.identity.internal.role.adapter.inbound.web.admin;

import cn.realm.cloud.framework.core.dto.PageResult;
import cn.realm.cloud.workforce.identity.internal.role.adapter.inbound.web.admin.converter.RoleAdminConverter;
import cn.realm.cloud.workforce.identity.internal.role.adapter.inbound.web.admin.dto.*;
import cn.realm.cloud.workforce.identity.internal.role.application.RoleDepartmentService;
import cn.realm.cloud.workforce.identity.internal.role.application.RoleMenuService;
import cn.realm.cloud.workforce.identity.internal.role.application.RoleService;
import cn.realm.cloud.workforce.identity.internal.role.infrastructure.persistence.RoleDO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Identity domain — Admin view.
 * 身份域【管理后台视图】角色资源入口。
 *
 * <p>面向管理后台的角色管理接口，使用 RBAC 权限模型
 * （如 {@code @PreAuthorize("@ss.hasPermission('iam:role:create')")}），
 * 提供角色的分页查询、创建、修改、删除、菜单分配、部门数据范围分配等管理能力。
 * 与其他视图（如应用端视图、开放平台视图）在权限模型、DTO契约、可见接口集合上严格隔离。
 *
 * <p>关联子资源端点（遵循 AIP 子资源规范：附属资源不独立根路径，挂靠主资源）：
 * <ul>
 *   <li>角色-菜单：{@code /admin/iam/role/{roleId}/menus}，全量替换角色菜单分配</li>
 *   <li>角色-部门：{@code /admin/iam/role/{roleId}/departments}，全量替换自定义数据范围（dataScope=2）下的部门访问范围</li>
 * </ul>
 * 角色菜单、角色部门属于角色的从属关联数据，生命周期依附角色，不作为独立顶层资源。
 * 对应存储表：{@code iam_role_menu}、{@code iam_role_department}。
 *
 * <p><b>分层职责边界（Clean Architecture）：</b>
 * <ul>
 *   <li>本 Controller：仅 HTTP 协议适配、OpenAPI 契约声明、权限注解校验、@Valid DTO 格式校验；</li>
 *   <li>业务规则校验（角色名称唯一、分页参数范围、数据范围约束、ID有效性校验）下沉至 Application Service；</li>
 *   <li>不包含任何业务判断、领域逻辑。</li>
 * </ul>
 *
 * <p>业务编排委托：{@link RoleService}、{@link RoleMenuService}、{@link RoleDepartmentService}。
 *
 * <p><b>依赖与事件联动：</b>
 * 菜单、部门ID有效性校验通过 {@code MenuFacade}、{@code OrganizationFacade} 完成；
 * 菜单/部门删除后的关联清理，订阅 {@code menu::events}、{@code organization::events} 异步处理。
 * 双向依赖无循环，需在 {@code role/package-info.java} 的 {@code allowedDependencies} 显式声明。
 *
 * @author QI Guang
 * @see <a href="https://google.aip.dev/122">AIP-122 Resource names</a>
 * @see <a href="https://google.aip.dev/132">AIP-132 Standard List methods</a>
 */
@RestController
@RequestMapping("/admin/iam/role")
@Tag(name = "Identity-Admin", description = "身份域｜B端管理后台接口")
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
    public PageResult<RoleResponse> page(@Valid RolePageRequest request) {
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
