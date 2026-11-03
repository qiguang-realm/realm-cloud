package cn.realm.cloud.system.server.organization.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新岗位请求。
 *
 * <p>不包含 {@code positionCode}：它是业务标识，创建后不可修改，
 * 服务层会用数据库中的原值覆盖。不包含 {@code status}：状态变更走
 * 独立的 {@code PUT /{id}/status} 端点，避免“编辑岗位时顺便改状态”
 * 绕过状态变更的独立语义。
 *
 * <p>字段命名与 {@link PositionCreateRequest} 保持一致，便于前端
 * 复用同一套表单字段。
 *
 * @param positionName 岗位名称
 * @param sortOrder    显示顺序，越小越靠前
 * @param description  描述
 * @author QI Guang
 */
@Schema(name = "PositionUpdateRequest", description = "更新岗位请求")
public record PositionUpdateRequest(

        @Schema(description = "岗位名称")
        @NotBlank @Size(max = 64)
        String positionName,

        @Schema(description = "显示顺序，越小越靠前")
        @Min(0)
        Integer sortOrder,

        @Schema(description = "描述")
        @Size(max = 256)
        String description
) {
}
