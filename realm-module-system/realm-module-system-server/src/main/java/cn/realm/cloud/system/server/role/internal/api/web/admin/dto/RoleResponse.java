package cn.realm.cloud.system.server.role.internal.api.web.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 角色响应（详情 / 分页项）。
 *
 * <p><b>不包含 {@code activeRoleCode} / {@code activeRoleName}：</b>
 * 逻辑删除唯一约束辅助列，纯持久化细节，不对前端暴露。
 * <b>不包含 {@code tenantId} / {@code isDeleted} / {@code version} /
 * {@code createBy} / {@code updateBy}：</b>租户、逻辑删除、并发控制
 * 与审计字段，不进入 HTTP 契约。
 *
 * <p>角色的菜单授权与自定义部门范围不在这里返回——它们通过独立的
 * {@code GET /{roleId}/menus} 和 {@code GET /{roleId}/departments}
 * 端点获取，避免详情响应随关联数据膨胀。
 *
 * @param id          角色 ID
 * @param roleCode    角色编码
 * @param roleName    角色名称
 * @param sortOrder   显示顺序
 * @param dataScope   数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人
 * @param status      状态：0-正常、1-停用
 * @param description 描述
 * @param createTime  创建时间
 * @author QI Guang
 */
@Schema(name = "RoleResponse", description = "角色响应")
public record RoleResponse(

        @Schema(description = "角色 ID")
        Long id,

        @Schema(description = "角色编码")
        String roleCode,

        @Schema(description = "角色名称")
        String roleName,

        @Schema(description = "显示顺序")
        Integer sortOrder,

        @Schema(description = "数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人")
        Integer dataScope,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status,

        @Schema(description = "描述")
        String description,

        @Schema(description = "创建时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        LocalDateTime createTime
) {
}
