package cn.realm.cloud.system.server.user.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UserUpdateRequest", description = "更新用户请求")
public record UserUpdateRequest(

        @Schema(description = "用户昵称")
        @NotBlank @Size(max = 64)
        String nickname,

        @Schema(description = "部门 ID")
        Long departmentId,

        @Schema(description = "手机号码")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phoneNumber,

        @Schema(description = "用户邮箱")
        @Email
        String email
) {
}
