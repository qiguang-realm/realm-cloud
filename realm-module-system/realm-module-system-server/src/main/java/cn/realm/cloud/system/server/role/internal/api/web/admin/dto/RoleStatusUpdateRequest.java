package cn.realm.cloud.system.server.role.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 变更角色状态请求。
 *
 * <p>状态变更走独立的 {@code PUT /{id}/status} 端点，不混在
 * {@link RoleUpdateRequest} 里，避免“编辑角色时顺便改状态”绕过
 * 状态变更的独立语义（如发布 {@code RoleStatusChangedEvent}、
 * 失效持有该角色的用户权限缓存）。
 *
 * <p>与 {@code UserStatusUpdateRequest} / {@code MenuStatusUpdateRequest}
 * / {@code DepartmentStatusUpdateRequest} / {@code PositionStatusUpdateRequest}
 * 同构：统一用 {@code record} + {@code @NotNull @Min(0) @Max(1)}，
 * 不再接收裸 {@code Integer}。
 *
 * @param status 状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "RoleStatusUpdateRequest", description = "变更角色状态请求")
public record RoleStatusUpdateRequest(

        @Schema(description = "状态：0-正常、1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status
) {
}
