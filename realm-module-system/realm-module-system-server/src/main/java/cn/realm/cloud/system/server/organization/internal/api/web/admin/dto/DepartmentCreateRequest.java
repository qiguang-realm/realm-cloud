package cn.realm.cloud.system.server.organization.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 创建部门请求。
 *
 * <p>{@code ancestors} 由服务端根据 {@code parentId} 计算，请求中不携带。
 *
 * <p>{@code managerUserId} 是用户 ID，服务层需通过 {@code UserFacade}
 * 校验该用户存在且未删除——这是 {@code organization → user} 的同步依赖，
 * 必须在 {@code organization/package-info.java} 的 {@code allowedDependencies}
 * 中声明 {@code "user"}，且 {@code user} 不得反向同步依赖 {@code organization}
 * 的内部类型。
 *
 * @param parentId       父部门 ID；根节点传 {@code null} 或 {@code 0}
 * @param departmentName 部门名称
 * @param sortOrder      显示顺序，越小越靠前
 * @param managerUserId  负责人用户 ID；可为 {@code null}
 * @param phoneNumber    联系电话
 * @param email          联系邮箱
 * @param status         状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "DepartmentCreateRequest", description = "创建部门请求")
public record DepartmentCreateRequest(

        @Schema(description = "父部门 ID；根节点传 null 或 0")
        Long parentId,

        @Schema(description = "部门名称")
        @NotBlank @Size(max = 64)
        String departmentName,

        @Schema(description = "显示顺序，越小越靠前")
        @Min(0)
        Integer sortOrder,

        @Schema(description = "负责人用户 ID")
        Long managerUserId,

        @Schema(description = "联系电话")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$|^0\\d{2,3}-?\\d{7,8}$",
                message = "联系电话格式不正确")
        String phoneNumber,

        @Schema(description = "联系邮箱")
        @Email
        @Size(max = 128)
        String email,

        @Schema(description = "状态：0-正常、1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status
) {
}
