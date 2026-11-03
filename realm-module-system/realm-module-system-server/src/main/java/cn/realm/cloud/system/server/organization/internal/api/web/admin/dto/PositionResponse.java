package cn.realm.cloud.system.server.organization.internal.api.web.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 岗位响应（详情 / 列表 / 分页项）。
 *
 * <p><b>不包含 {@code activePositionCode} / {@code activePositionName}：</b>
 * 逻辑删除唯一约束辅助列，纯持久化细节，不对前端暴露。
 * <b>不包含 {@code tenantId} / {@code isDeleted} / {@code version}
 * / {@code createBy} / {@code updateBy}：</b>租户、逻辑删除、并发控制
 * 与审计字段，不进入 HTTP 契约。
 *
 * @param id           岗位 ID
 * @param positionCode 岗位编码
 * @param positionName 岗位名称
 * @param sortOrder    显示顺序
 * @param status       状态：0-正常、1-停用
 * @param description  描述
 * @param createTime   创建时间
 * @author QI Guang
 */
@Schema(name = "PositionResponse", description = "岗位响应")
public record PositionResponse(

        @Schema(description = "岗位 ID")
        Long id,

        @Schema(description = "岗位编码")
        String positionCode,

        @Schema(description = "岗位名称")
        String positionName,

        @Schema(description = "显示顺序")
        Integer sortOrder,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status,

        @Schema(description = "描述")
        String description,

        @Schema(description = "创建时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        LocalDateTime createTime
) {
}
