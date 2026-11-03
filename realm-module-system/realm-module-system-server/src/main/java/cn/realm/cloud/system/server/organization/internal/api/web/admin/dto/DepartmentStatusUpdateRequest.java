package cn.realm.cloud.system.server.organization.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 变更部门状态请求。
 *
 * <p>状态变更走独立的 {@code PUT /{id}/status} 端点，不混在
 * {@link DepartmentUpdateRequest} 里，避免“编辑部门时顺便改状态”
 * 绕过状态变更的独立语义（如发布 {@code DepartmentStatusChangedEvent}）。
 *
 * @param status 状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "DepartmentStatusUpdateRequest", description = "变更部门状态请求")
public record DepartmentStatusUpdateRequest(

        @Schema(description = "状态：0-正常、1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status
) {
}
