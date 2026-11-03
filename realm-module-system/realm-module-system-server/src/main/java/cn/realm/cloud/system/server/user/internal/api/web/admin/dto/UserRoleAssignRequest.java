package cn.realm.cloud.system.server.user.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(name = "UserRoleAssignRequest", description = "全量替换用户角色请求")
public record UserRoleAssignRequest(

        @Schema(description = "角色 ID 列表；空列表表示清空该用户的所有角色")
        @NotNull
        List<@NotNull Long> roleIds
) {
}
