package cn.realm.cloud.system.server.user.internal.api.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserProfileResponse", description = "个人资料响应（仅当前用户可见字段）")
public record UserProfileResponse(

        @Schema(description = "用户 ID")
        Long id,

        @Schema(description = "用户昵称")
        String nickname,

        @Schema(description = "头像地址")
        String avatarUrl,

        @Schema(description = "脱敏后的手机号")
        String maskedMobile
) {
}
