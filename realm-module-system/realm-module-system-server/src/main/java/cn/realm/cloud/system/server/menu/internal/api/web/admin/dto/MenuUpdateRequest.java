package cn.realm.cloud.system.server.menu.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 更新菜单请求。
 *
 * <p>不包含 {@code parentId} 和 {@code menuType}：前者由独立的 move 端点处理
 * （会触发整棵子树的 {@code ancestors} 重算），后者是节点的结构性属性，
 * 创建后变更需要额外的关联校验，不通过普通编辑修改。
 *
 * <p>不包含 {@code status}：状态变更走独立的 {@code PUT /{id}/status} 端点，
 * 避免“编辑菜单时顺便改状态”绕过状态变更的独立语义。
 *
 * @author QI Guang
 */
@Schema(name = "MenuUpdateRequest", description = "更新菜单请求")
public record MenuUpdateRequest(

        @Schema(description = "菜单名称")
        @NotBlank @Size(max = 64)
        String menuName,

        @Schema(description = "显示顺序，越小越靠前")
        @Min(0)
        Integer sortOrder,

        @Schema(description = "路由地址")
        @Size(max = 256)
        String path,

        @Schema(description = "前端组件路径")
        @Size(max = 256)
        String component,

        @Schema(description = "前端组件名")
        @Size(max = 128)
        String componentName,

        @Schema(description = "权限标识，按钮必填")
        @Size(max = 128)
        String permissionCode,

        @Schema(description = "菜单图标")
        @Size(max = 128)
        String icon,

        @Schema(description = "是否可见：0-否、1-是")
        @NotNull @Min(0) @Max(1)
        Integer isVisible,

        @Schema(description = "是否缓存：0-否、1-是")
        @Min(0) @Max(1)
        Integer isKeepAlive,

        @Schema(description = "是否总是显示：0-否、1-是")
        @Min(0) @Max(1)
        Integer isAlwaysShow
) {
}
