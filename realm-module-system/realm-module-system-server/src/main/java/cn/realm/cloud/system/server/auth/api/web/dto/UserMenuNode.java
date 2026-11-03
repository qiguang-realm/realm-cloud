package cn.realm.cloud.system.server.auth.api.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 前端路由所需的菜单节点。
 *
 * <p>字段与 {@code vue-element-admin} 的 {@code asyncRoutes} 结构对齐：
 * <ul>
 *   <li>{@code path} / {@code component} / {@code name} → 路由定义</li>
 *   <li>{@code meta.title} / {@code meta.icon} → 侧边栏渲染</li>
 *   <li>{@code children} → 嵌套路由</li>
 * </ul>
 *
 * <p><b>为什么不直接复用 {@code MenuDto}：</b>{@code MenuDto} 是跨模块
 * 只读契约，包含 {@code permissionCode} / {@code status} 等管理字段；
 * 本类型是“前端路由视图”，只包含路由需要的字段，并额外把
 * {@code menuName} 重命名为 {@code meta.title}——这是前端框架的约定，
 * 不应该反向污染 {@code MenuDto}。
 *
 * @param id        菜单 ID（前端用作路由的 key）
 * @param path      路由路径
 * @param component 组件路径（如 {@code admin/user}）
 * @param name      路由名称（Vue Router 的 {@code name}，用于 keep-alive）
 * @param meta      路由元信息
 * @param children  子菜单
 * @author QI Guang
 */
@Schema(name = "UserMenuNode", description = "前端路由所需的菜单节点")
public record UserMenuNode(

        @Schema(description = "菜单 ID")
        Long id,

        @Schema(description = "路由路径")
        String path,

        @Schema(description = "组件路径")
        String component,

        @Schema(description = "路由名称")
        String name,

        @Schema(description = "路由元信息")
        Meta meta,

        @Schema(description = "子菜单")
        List<UserMenuNode> children
) {
    public UserMenuNode {
        children = children == null ? List.of() : List.copyOf(children);
    }

    /**
     * 路由元信息。
     *
     * @param title 菜单标题（侧边栏显示）
     * @param icon  菜单图标（如 {@code el-icon-user}）
     */
    @Schema(name = "UserMenuMeta", description = "路由元信息")
    public record Meta(
            @Schema(description = "菜单标题")
            String title,

            @Schema(description = "菜单图标")
            String icon
    ) {
    }
}
