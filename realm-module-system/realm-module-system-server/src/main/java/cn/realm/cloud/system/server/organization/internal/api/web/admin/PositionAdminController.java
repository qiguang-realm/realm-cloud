package cn.realm.cloud.system.server.organization.internal.api.web.admin;

import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.converter.PositionAdminConverter;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.*;
import cn.realm.cloud.system.server.organization.internal.application.PositionService;
import cn.realm.cloud.system.server.organization.internal.persistence.PositionDO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 岗位模块管理后台接口入口。
 *
 * <p>岗位是组织架构模块下的一等实体，拥有独立的创建、更新、启停与逻辑删除生命周期。
 * 与部门不同，岗位是<b>平铺列表</b>而非树形结构，因此提供分页查询而非树形端点。
 *
 * <p>本类只做 HTTP 协议适配与权限校验，业务委托给 {@link PositionService}。
 *
 * <p><b>校验职责：</b>{@code @Valid} 触发 DTO 上的格式校验
 * （{@code @NotBlank} / {@code @Min} / {@code @Max}），业务规则校验
 * （如 {@code pageSize} 范围、岗位名称唯一性）由 Service 层完成——
 * 本类只做 HTTP 协议适配与权限校验，不做业务判断。
 *
 * <p><b>删除语义（与部门保持一致）：</b>删除前不查询 {@code iam_user_position}
 * ——该表归 {@code user} 模块所有，organization 查询它会形成反向数据依赖，
 * 与 {@code user → organization} 构成循环。用户岗位关联由 {@code user} 模块
 * 监听 {@code PositionDeletedEvent} 异步清理，最终一致。若业务确实要求
 * “被引用的岗位不允许删除”，需先在 ADR 中重新界定 {@code iam_user_position}
 * 的所有权，而不是在本模块直接查表。
 *
 * @author QI Guang
 * @see cn.realm.cloud.system.server.organization.internal.api.web.admin.DepartmentAdminController
 */
@RestController
@RequestMapping("/admin/iam/position")
@RequiredArgsConstructor
public class PositionAdminController {

    private final PositionService positionService;

    /**
     * 创建岗位。
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:position:create')")
    public Long create(@Valid @RequestBody PositionCreateRequest request) {
        return positionService.createPosition(
                PositionAdminConverter.fromCreateRequest(request));
    }

    /**
     * 更新岗位。
     *
     * <p>岗位编码 {@code positionCode} 创建后不可修改——它是业务标识，
     * 被 {@code iam_user_position} 等关联表引用时若变更会导致追溯困难。
     * 更新请求中不包含该字段，服务层会用数据库中的原值覆盖，Repository
     * 层也不 set 该字段，形成双重保护。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:position:update')")
    public void update(@PathVariable Long id,
                       @Valid @RequestBody PositionUpdateRequest request) {
        PositionDO position = positionService.getPosition(id);
        PositionAdminConverter.applyUpdateRequest(request, position);
        positionService.updatePosition(position);
    }

    /**
     * 查询岗位详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:position:query')")
    public PositionResponse get(@PathVariable Long id) {
        return PositionAdminConverter.toResponse(positionService.getPosition(id));
    }

    /**
     * 分页查询岗位，供岗位管理页、用户编辑页的岗位选择器使用。
     *
     * <p>岗位是平铺列表，没有层级关系，用分页而非树形返回。
     * {@code PositionPageRequest} 为 record，作为 GET 查询参数绑定；
     * {@code @ParameterObject} 让 springdoc 按字段展开文档，而非把整个
     * record 渲染成一个请求体。若项目未引入 springdoc，可去掉该注解，
     * 绑定行为不变，仅文档退化。
     *
     * <p><b>参数传递：</b>{@code request.toPageParam()} 把分页部分剥离为
     * 框架层的 {@code PageParam}，{@code request.positionName()} /
     * {@code request.status()} 作为查询条件独立传递。Service 不感知
     * HTTP 契约，只接收框架参数 + 业务参数——与
     * {@code UserAdminController.page} / {@code RoleAdminController.page}
     * 保持一致。
     */
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:position:query')")
    public PageResult<PositionResponse> page(
            @Valid @ParameterObject PositionPageRequest request) {
        return PositionAdminConverter.toPageResponse(positionService.page(
                request.toPageParam(), request.positionName(), request.status()));
    }

    /**
     * 变更岗位状态（启用 / 停用）。
     *
     * <p>状态未实际变化时 Service 层会短路，不写库、不发事件。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermission('system:position:update')")
    public void changeStatus(@PathVariable Long id,
                             @Valid @RequestBody PositionStatusUpdateRequest request) {
        positionService.changeStatus(id, request.status());
    }

    /**
     * 删除岗位。
     *
     * <p>删除前只做岗位自身的校验，不查询 {@code iam_user_position}
     * （该表归 {@code user} 模块所有）。用户岗位关联由 {@code user} 模块
     * 监听 {@code PositionDeletedEvent} 异步清理。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:position:delete')")
    public void delete(@PathVariable Long id) {
        positionService.deletePosition(id);
    }
}
