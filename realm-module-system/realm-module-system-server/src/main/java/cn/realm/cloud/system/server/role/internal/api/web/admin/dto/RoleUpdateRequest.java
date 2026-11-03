package cn.realm.cloud.system.server.role.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 更新角色请求。
 *
 * <p>不包含 {@code roleCode}：它是业务标识，创建后不可修改，
 * 服务层会用数据库中的原值覆盖。不包含 {@code status}：状态变更走
 * 独立的 {@code PUT /{id}/status} 端点，避免“编辑角色时顺便改状态”
 * 绕过状态变更的独立语义（如发布 {@code RoleStatusChangedEvent}）。
 *
 * <p><b>{@code dataScope} 变更的处理：</b>本请求包含 {@code dataScope}，
 * 允许在编辑时调整数据范围。如果从 {@code 2}（自定义）改为其他值，
 * 服务层需要清理 {@code iam_role_department} 中的残留关联——这部分
 * 逻辑在 {@code RoleServiceImpl.updateRole} 中完成，不在 DTO 层处理。
 *
 * <p>字段命名与 {@link RoleCreateRequest} 保持一致（{@code roleCode} 除外，
 * 因为不可修改），便于前端复用同一套表单字段。
 *
 * @param roleName    角色名称
 * @param sortOrder   显示顺序，越小越靠前
 * @param dataScope   数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人
 * @param description 描述
 * @author QI Guang
 */
@Schema(name = "RoleUpdateRequest", description = "更新角色请求")
public record RoleUpdateRequest(

        @Schema(description = "角色名称")
        @NotBlank @Size(max = 64)
        String roleName,

        @Schema(description = "显示顺序，越小越靠前")
        @Min(0)
        Integer sortOrder,

        @Schema(description = "数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人")
        @NotNull @Min(1) @Max(5)
        Integer dataScope,

        @Schema(description = "描述")
        @Size(max = 256)
        String description
) {
}
