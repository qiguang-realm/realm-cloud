package cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin;

import cn.realm.cloud.framework.core.dto.PageResult;
import cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin.converter.PositionAdminConverter;
import cn.realm.cloud.workforce.identity.internal.organization.adapter.inbound.web.admin.dto.*;
import cn.realm.cloud.workforce.identity.internal.organization.application.PositionService;
import cn.realm.cloud.workforce.identity.internal.organization.infrastructure.persistence.PositionDO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Identity domain — Admin view.
 * 身份域【管理后台视图】岗位资源入口。
 *
 * <p>面向管理后台的岗位管理接口，使用 RBAC 权限模型
 * （如 {@code @PreAuthorize("@ss.hasPermission('iam:position:create')")}），
 * 提供岗位的分页查询、创建、修改、状态变更、逻辑删除等管理能力。
 * 与其他视图（如应用端视图、开放平台视图）在权限模型、DTO契约、可见接口集合上严格隔离。
 *
 * <p>岗位是组织架构下一等实体，为平铺列表结构，无树形层级，因此提供分页查询而非树形端点。
 * 对应存储表：{@code iam_position}。
 *
 * <p><b>分层职责边界（Clean Architecture）：</b>
 * <ul>
 *   <li>本 Controller：仅 HTTP 协议适配、OpenAPI 契约声明、权限注解校验、@Valid DTO 格式校验；</li>
 *   <li>业务规则校验（岗位名称唯一、分页参数范围等）下沉至 Application Service；</li>
 *   <li>不包含任何业务判断、领域逻辑。</li>
 * </ul>
 *
 * <p>删除语义（与部门保持一致）：删除前不查询 {@code iam_user_position}，该表归属 {@code user} 模块，
 * 本模块直接查询会形成反向依赖，与 {@code user → organization} 构成循环依赖。用户岗位关联由 {@code user} 模块
 * 监听 {@code PositionDeletedEvent} 异步清理，保障最终一致性。若业务需要“被引用岗位禁止删除”的强约束，
 * 需先通过 ADR 重新界定 {@code iam_user_position} 所有权，不在本模块跨模块查表校验。
 *
 * <p>业务编排委托：{@link PositionService}。
 *
 * @author QI Guang
 * @see DepartmentAdminController
 * @see <a href="https://google.aip.dev/122">AIP-122 Resource names</a>
 * @see <a href="https://google.aip.dev/132">AIP-132 Standard List methods</a>
 */
@RestController
@RequestMapping("/admin/iam/position")
@Tag(name = "Identity-Admin", description = "身份域｜B端管理后台接口")
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
    public PageResult<PositionResponse> page(@Valid PositionPageRequest request) {
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
