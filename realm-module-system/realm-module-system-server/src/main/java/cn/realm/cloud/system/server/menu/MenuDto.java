package cn.realm.cloud.system.server.menu;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 菜单只读契约。
 *
 * <p>跨模块传递的菜单信息，<b>不包含</b>审计字段、逻辑删除标记、
 * {@code activePermissionCode}、{@code ancestors} 等纯持久化辅助列。
 *
 * <p><b>字段扩展的依据（相比早期版本）：</b>
 * 早期版本假设 {@code MenuDto} 只被 {@code role} 模块用于“校验菜单 ID 有效性”，
 * 因此刻意裁掉了 {@code path} / {@code component} / {@code icon} 等前端字段。
 * 现在 {@code /auth/profile} 需要把用户可访问的菜单组装成前端路由树，
 * 这些字段成为必需：
 * <ul>
 *   <li>{@code sortOrder}——组装树时每层按此排序</li>
 *   <li>{@code path} / {@code component} / {@code componentName}——前端路由的三要素</li>
 *   <li>{@code icon}——侧边栏图标</li>
 *   <li>{@code isVisible}——前端据此决定是否在侧边栏渲染（隐藏菜单仍可路由）</li>
 * </ul>
 *
 * <p><b>仍然不包含：</b>
 * <ul>
 *   <li>{@code ancestors}——服务端子树查询辅助列，消费方不需要</li>
 *   <li>{@code activePermissionCode}——逻辑删除唯一约束辅助列</li>
 *   <li>{@code tenantId} / {@code isDeleted} / {@code version} / 审计字段——内部管理字段</li>
 *   <li>{@code isKeepAlive} / {@code isAlwaysShow}——当前前端不需要；
 *       如果未来前端需要 keep-alive 和 alwaysShow 语义，再补</li>
 * </ul>
 *
 * <p>字段变更会影响所有依赖 {@code menu} 模块的其他模块
 * （{@code role} / {@code auth}），新增字段需评估兼容性。
 *
 * @param id             菜单 ID
 * @param parentId       父菜单 ID；根节点为 {@code null} 或 {@code 0}
 * @param menuName       菜单名称
 * @param menuType       类型：1-目录、2-菜单、3-按钮
 * @param sortOrder      显示顺序，越小越靠前；可为 {@code null}
 * @param path           路由地址；按钮类型通常为 {@code null}
 * @param component      前端组件路径；按钮类型通常为 {@code null}
 * @param componentName  前端组件名（Vue Router 的 {@code name}，用于 keep-alive）
 * @param icon           菜单图标
 * @param permissionCode 权限标识；目录/菜单可为 {@code null}
 * @param status         状态：0-正常、1-停用
 * @param isVisible      是否可见：0-否、1-是
 * @author QI Guang
 */
@Schema(name = "MenuDto", description = "菜单只读契约")
public record MenuDto(

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

        @Schema(description = "菜单图标")
        String icon,

        @Schema(description = "权限标识")
        String permissionCode,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status,

        @Schema(description = "是否可见：0-否、1-是")
        Integer isVisible
) {
}
