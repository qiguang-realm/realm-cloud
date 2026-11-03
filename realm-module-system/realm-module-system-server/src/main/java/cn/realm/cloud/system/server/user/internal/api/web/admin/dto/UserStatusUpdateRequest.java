package cn.realm.cloud.system.server.user.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UserStatusUpdateRequest", description = "变更用户状态请求")
public record UserStatusUpdateRequest(

        @Schema(description = "状态：0-正常；1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status
) {
}
