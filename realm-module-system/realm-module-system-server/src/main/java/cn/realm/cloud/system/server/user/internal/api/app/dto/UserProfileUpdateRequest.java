package cn.realm.cloud.system.server.user.internal.api.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UserProfileUpdateRequest", description = "更新个人资料请求")
public record UserProfileUpdateRequest(

        @Schema(description = "用户昵称")
        @NotBlank @Size(max = 64)
        String nickname,

        @Schema(description = "头像地址")
        @Size(max = 512)
        String avatarUrl
) {
}
