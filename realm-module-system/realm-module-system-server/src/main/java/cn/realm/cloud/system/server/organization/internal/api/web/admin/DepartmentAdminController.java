package cn.realm.cloud.system.server.organization.internal.api.web.admin;

import cn.realm.cloud.system.server.organization.internal.api.web.admin.converter.DepartmentAdminConverter;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.DepartmentCreateRequest;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.DepartmentResponse;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.DepartmentStatusUpdateRequest;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.DepartmentUpdateRequest;
import cn.realm.cloud.system.server.organization.internal.application.DepartmentService;
import cn.realm.cloud.system.server.organization.internal.persistence.DepartmentDO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门模块管理后台接口入口。
 *
 * <p>部门是组织架构模块下的一等实体，以 {@code parentId} + {@code ancestors}
 * 组织为树形结构，拥有独立的创建、更新、启停与逻辑删除生命周期。
 *
 * <p>本类只做 HTTP 协议适配与权限校验，业务委托给 {@link DepartmentService}。
 * {@code ancestors} 的拼接、移动节点时子孙链的重算、删除前的子部门校验、
 * 树形结构组装均由应用服务在事务内完成——与菜单模块保持同一套职责划分：
 * 树组装在 Service，Controller 直接透传结果。
 *
 * <p><b>删除语义：</b>删除前只校验是否存在未删除的子部门；
 * 用户对部门的引用（{@code UserDO.departmentId}）由 {@code user} 模块
 * 监听 {@code DepartmentDeletedEvent} 异步清理，本模块不反向依赖 {@code user}，
 * 也不查询 {@code user} 模块持有的表。
 *
 * @author QI Guang
 * @see cn.realm.cloud.system.server.organization.internal.api.web.admin.PositionAdminController
 */
@RestController
@RequestMapping("/admin/iam/department")
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
