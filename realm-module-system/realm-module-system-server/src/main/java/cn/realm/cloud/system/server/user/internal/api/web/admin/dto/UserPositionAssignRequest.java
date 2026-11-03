package cn.realm.cloud.system.server.user.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(name = "UserPositionAssignRequest", description = "全量替换用户岗位请求")
public record UserPositionAssignRequest(

        @Schema(description = "岗位 ID 列表；空列表表示清空该用户的所有岗位")
        @NotNull
        List<@NotNull Long> positionIds
) {
}
