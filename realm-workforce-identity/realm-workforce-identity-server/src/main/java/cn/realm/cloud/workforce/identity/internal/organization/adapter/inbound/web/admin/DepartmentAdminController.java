package cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin;

import cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin.converter.DepartmentAdminConverter;
import cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin.dto.DepartmentCreateRequest;
import cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin.dto.DepartmentResponse;
import cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin.dto.DepartmentStatusUpdateRequest;
import cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin.dto.DepartmentUpdateRequest;
import cn.realm.cloud.workforce.identity.internal.organization.application.DepartmentService;
import cn.realm.cloud.workforce.identity.internal.organization.infrastructure.persistence.DepartmentDO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Identity domain — Admin view.
 * 身份域【管理后台视图】部门资源入口。
 *
 * <p>面向管理后台的部门管理接口，使用 RBAC 权限模型
 * （如 {@code @PreAuthorize("@ss.hasPermission('iam:department:create')")}），
 * 提供部门的树形查询、创建、修改、状态变更、逻辑删除等管理能力。
 * 与其他视图（如应用端视图、开放平台视图）在权限模型、DTO契约、可见接口集合上严格隔离。
 *
 * <p>部门是组织架构下一等实体，依托 {@code parentId} + {@code ancestors} 维护树形层级；
 * 节点移动、祖先链重算、子节点校验、树形组装逻辑下沉至应用服务。
 * 对应存储表：{@code iam_department}。
 *
 * <p><b>分层职责边界（Clean Architecture）：</b>
 * <ul>
 *   <li>本 Controller：仅 HTTP 协议适配、OpenAPI 契约声明、权限注解校验、@Valid DTO 格式校验；</li>
 *   <li>业务规则校验（父节点合法性、节点移动约束、是否存在子部门、树形数据组装）下沉至 Application Service；</li>
 *   <li>不包含任何业务判断、领域逻辑。</li>
 * </ul>
 *
 * <p>删除语义：删除前仅校验是否存在未删除子部门；用户与部门的关联引用由 {@code user} 模块监听
 * {@code DepartmentDeletedEvent} 异步清理，本模块不反向依赖 user 模块，不跨模块查询用户表。
 *
 * <p>业务编排委托：{@link DepartmentService}。
 *
 * @author QI Guang
 * @see cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin.PositionAdminController
 * @see <a href="https://google.aip.dev/122">AIP-122 Resource names</a>
 * @see <a href="https://google.aip.dev/132">AIP-132 Standard List methods</a>
 */
@RestController
@RequestMapping("/admin/iam/department")
@Tag(name = "Identity-Admin", description = "身份域｜B端管理后台接口")
@RequiredArgsConstructor
public class DepartmentAdminController {

    private final DepartmentService departmentService;

    /**
     * 创建部门。
     *
     * <p>{@code ancestors} 由服务端根据 {@code parentId} 计算，请求中不需要携带。
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:department:create')")
    public Long create(@Valid @RequestBody DepartmentCreateRequest request) {
        return departmentService.createDepartment(
                DepartmentAdminConverter.fromCreateRequest(request));
    }

    /**
     * 更新部门。
     *
     * <p>不支持通过本端点修改 {@code parentId}——移动节点会触发整棵子树的
     * {@code ancestors} 重算，由独立端点处理。服务层会用数据库中的原值
     * 覆盖 {@code parentId} 和 {@code ancestors}，双重保护。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:department:update')")
    public void update(@PathVariable Long id,
                       @Valid @RequestBody DepartmentUpdateRequest request) {
        DepartmentDO department = departmentService.getDepartment(id);
        DepartmentAdminConverter.applyUpdateRequest(request, department);
        departmentService.updateDepartment(department);
    }

    /**
     * 查询部门详情（不含子节点）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:department:query')")
    public DepartmentResponse get(@PathVariable Long id) {
        return DepartmentAdminConverter.toResponse(departmentService.getDepartment(id));
    }

    /**
     * 查询完整部门树（不分页），供部门管理页、用户编辑页的部门选择器、
     * 组织架构图使用。
     *
     * <p>树组装在 {@link DepartmentService#listTree()} 内完成，
     * Controller 直接透传服务层结果，不再经过 Converter 或手动组树。
     */
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPermission('system:department:query')")
    public List<DepartmentResponse> tree() {
        return departmentService.listTree();
    }

    /**
     * 变更部门状态（启用 / 停用）。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermission('system:department:update')")
    public void changeStatus(@PathVariable Long id,
                             @Valid @RequestBody DepartmentStatusUpdateRequest request) {
        departmentService.changeStatus(id, request.status());
    }

    /**
     * 删除部门。
     *
     * <p>服务端在删除前只校验是否存在未删除的子部门，有则拒绝。
     * 用户对部门的引用由 {@code user} 模块监听 {@code DepartmentDeletedEvent}
     * 异步清理，本模块不反向依赖 {@code user}，也不查询 {@code iam_user} 表。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:department:delete')")
    public void delete(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
    }
}
