package cn.realm.cloud.system.server.menu.internal.api.web.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单响应（详情 / 列表 / 树节点）。
 *
 * <p>{@code children} 为 {@code null} 时归一为空列表，前端可直接遍历。
 *
 * <p><b>不包含 {@code ancestors}：</b>服务端子树查询用的持久化辅助列，
 * 前端渲染树只需要 {@code parentId} + {@code children}。
 * <b>不包含 {@code activePermissionCode}：</b>逻辑删除唯一约束辅助列，纯持久化细节。
 *
 * <p>本类型不可变；树组装由 {@code MenuService} 自底向上构造，
 * 每次构造时传入已组装好的 {@code children} 列表。
 *
 * @author QI Guang
 */
@Schema(name = "MenuResponse", description = "菜单响应")
public record MenuResponse(

        @Schema(description = "菜单 ID")
        Long id,

        @Schema(description = "父菜单 ID；根节点为 null 或 0")
        Long parentId,

        @Schema(description = "菜单名称")
        String menuName,

        @Schema(description = "类型：1-目录、2-菜单、3-按钮")
        Integer menuType,

        @Schema(description = "显示顺序")
        Integer sortOrder,

        @Schema(description = "路由地址")
        String path,

        @Schema(description = "前端组件路径")
        String component,

        @Schema(description = "前端组件名")
        String componentName,

        @Schema(description = "权限标识")
        String permissionCode,

        @Schema(description = "菜单图标")
        String icon,

        @Schema(description = "是否可见：0-否、1-是")
        Integer isVisible,

        @Schema(description = "是否缓存：0-否、1-是")
        Integer isKeepAlive,

        @Schema(description = "是否总是显示：0-否、1-是")
        Integer isAlwaysShow,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status,

        @Schema(description = "创建时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        LocalDateTime createTime,

        @Schema(description = "子节点列表")
        List<MenuResponse> children
) {

    /**
     * 紧凑构造器：{@code children} 为 {@code null} 时归一为空列表，
     * 非空时复制为不可变列表，保证 {@code MenuResponse} 整体不可变。
     */
    public MenuResponse {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
