package cn.realm.cloud.system.server.role.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 创建角色请求。
 *
 * <p>{@code roleCode} 是租户内唯一的业务标识，创建后不可修改——
 * 它被 {@code iam_user_role} 等关联表引用，变更会导致追溯困难。
 *
 * <p>{@code dataScope} 为 {@code 2}（自定义）时，部门范围不通过本请求携带，
 * 而是创建成功后由 {@code PUT /{roleId}/departments} 单独设置。这是为了
 * 避免“创建角色 + 分配部门”在一个请求里耦合，也让自定义范围的支持
 * 与 {@code RoleDepartmentService} 的校验（部门存在性）解耦。
 *
 * @param roleCode    角色编码，租户内唯一；创建后不可修改
 * @param roleName    角色名称
 * @param sortOrder   显示顺序，越小越靠前
 * @param dataScope   数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人
 * @param status      状态：0-正常、1-停用
 * @param description 描述
 * @author QI Guang
 */
@Schema(name = "RoleCreateRequest", description = "创建角色请求")
public record RoleCreateRequest(

        @Schema(description = "角色编码，租户内唯一；创建后不可修改")
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
                message = "角色编码只能以字母开头，由字母、数字、下划线组成")
        String roleCode,

        @Schema(description = "角色名称")
        @NotBlank @Size(max = 64)
        String roleName,

        @Schema(description = "显示顺序，越小越靠前")
        @Min(0)
        Integer sortOrder,

        @Schema(description = "数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人")
        @NotNull @Min(1) @Max(5)
        Integer dataScope,

        @Schema(description = "状态：0-正常、1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status,

        @Schema(description = "描述")
        @Size(max = 256)
        String description
) {
}
