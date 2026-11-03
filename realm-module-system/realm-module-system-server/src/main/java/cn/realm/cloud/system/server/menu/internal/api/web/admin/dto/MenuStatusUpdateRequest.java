package cn.realm.cloud.system.server.menu.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "MenuStatusUpdateRequest", description = "变更菜单状态请求")
public record MenuStatusUpdateRequest(

        @Schema(description = "状态：0-正常；1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status
) {
}
