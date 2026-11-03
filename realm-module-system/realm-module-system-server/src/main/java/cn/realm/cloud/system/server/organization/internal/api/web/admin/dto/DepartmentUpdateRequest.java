package cn.realm.cloud.system.server.organization.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 更新部门请求。
 *
 * <p>不包含 {@code parentId}：移动节点会触发整棵子树的 {@code ancestors}
 * 重算，由独立端点处理。不包含 {@code status}：状态变更走独立的
 * {@code PUT /{id}/status} 端点，避免“编辑部门时顺便改状态”绕过
 * 状态变更的独立语义。
 *
 * <p>字段命名与 {@link DepartmentCreateRequest} 保持一致，便于前端
 * 复用同一套表单字段和 Converter 的映射逻辑。
 *
 * @param departmentName 部门名称
 * @param sortOrder      显示顺序，越小越靠前
 * @param managerUserId  负责人用户 ID
 * @param phoneNumber    联系电话
 * @param email          联系邮箱
 * @author QI Guang
 */
@Schema(name = "DepartmentUpdateRequest", description = "更新部门请求")
public record DepartmentUpdateRequest(

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
        String email
) {
}
