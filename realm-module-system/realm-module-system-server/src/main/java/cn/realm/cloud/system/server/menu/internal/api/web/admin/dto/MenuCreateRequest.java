package cn.realm.cloud.system.server.menu.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 创建菜单请求。
 *
 * <p>{@code ancestors} 由服务端根据 {@code parentId} 计算，请求中不携带。
 *
 * <p><b>条件校验（由服务层完成）：</b>
 * <ul>
 *   <li>{@code menuType = 1}（目录）、{@code 2}（菜单）：{@code path} 必填</li>
 *   <li>{@code menuType = 3}（按钮）：{@code permissionCode} 必填，{@code path} 可为空</li>
 * </ul>
 *
 * @param parentId       父菜单 ID；根节点传 {@code null} 或 {@code 0}
 * @param menuName       菜单名称
 * @param menuType       类型：1-目录、2-菜单、3-按钮
 * @param sortOrder      显示顺序，越小越靠前
 * @param path           路由地址
 * @param component      前端组件路径
 * @param componentName  前端组件名
 * @param permissionCode 权限标识，如 {@code system:user:create}
 * @param icon           菜单图标
 * @param isVisible      是否可见：0-否、1-是
 * @param isKeepAlive    是否缓存：0-否、1-是
 * @param isAlwaysShow   是否总是显示：0-否、1-是
 * @param status         状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "MenuCreateRequest", description = "创建菜单请求")
public record MenuCreateRequest(

        @Schema(description = "父菜单 ID；根节点传 null 或 0")
        Long parentId,

        @Schema(description = "菜单名称")
        @NotBlank @Size(max = 64)
        String menuName,

        @Schema(description = "类型：1-目录、2-菜单、3-按钮")
        @NotNull @Min(1) @Max(3)
        Integer menuType,

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
        Integer isAlwaysShow,

        @Schema(description = "状态：0-正常、1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status
) {
}
