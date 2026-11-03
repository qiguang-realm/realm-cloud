package cn.realm.cloud.system.server.organization.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 变更岗位状态请求。
 *
 * <p>状态变更走独立的 {@code PUT /{id}/status} 端点，不混在
 * {@link PositionUpdateRequest} 里，避免“编辑岗位时顺便改状态”绕过
 * 状态变更的独立语义（如发布 {@code PositionStatusChangedEvent}、
 * 失效持有该岗位的用户权限缓存）。
 *
 * <p>与 {@code UserStatusUpdateRequest} / {@code MenuStatusUpdateRequest}
 * / {@code DepartmentStatusUpdateRequest} / {@code RoleStatusUpdateRequest}
 * 同构：统一用 {@code record} + {@code @NotNull @Min(0) @Max(1)}，
 * 不再接收裸 {@code Integer}。
 *
 * <p><b>为什么不再用裸 {@code Integer}：</b>裸 {@code Integer} 作为
 * {@code @RequestBody} 要求前端发送 {@code 0} 或 {@code 1} 而不是
 * {@code {"status": 0}}，Swagger 文档中会渲染成一个无字段名的请求体，
 * 且无法附加校验注解。包装成 record 后，请求体语义明确、可校验、文档友好。
 *
 * @param status 状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "PositionStatusUpdateRequest", description = "变更岗位状态请求")
public record PositionStatusUpdateRequest(

        @Schema(description = "状态：0-正常、1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status
) {
}
