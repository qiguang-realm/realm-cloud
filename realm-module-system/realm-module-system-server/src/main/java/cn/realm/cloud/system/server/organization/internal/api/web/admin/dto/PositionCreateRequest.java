package cn.realm.cloud.system.server.organization.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 创建岗位请求。
 *
 * <p>{@code positionCode} 是租户内唯一的业务标识，创建后不可修改——
 * 它被 {@code iam_user_position} 等关联表引用，变更会导致追溯困难。
 *
 * @param positionCode 岗位编码，租户内唯一；创建后不可修改
 * @param positionName 岗位名称
 * @param sortOrder    显示顺序，越小越靠前
 * @param status       状态：0-正常、1-停用
 * @param description  描述
 * @author QI Guang
 */
@Schema(name = "PositionCreateRequest", description = "创建岗位请求")
public record PositionCreateRequest(

        @Schema(description = "岗位编码，租户内唯一；创建后不可修改")
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
                message = "岗位编码只能以字母开头，由字母、数字、下划线组成")
        String positionCode,

        @Schema(description = "岗位名称")
        @NotBlank @Size(max = 64)
        String positionName,

        @Schema(description = "显示顺序，越小越靠前")
        @Min(0)
        Integer sortOrder,

        @Schema(description = "状态：0-正常、1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status,

        @Schema(description = "描述")
        @Size(max = 256)
        String description
) {
}
