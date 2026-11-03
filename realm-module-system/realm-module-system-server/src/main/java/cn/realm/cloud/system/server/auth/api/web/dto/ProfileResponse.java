package cn.realm.cloud.system.server.auth.api.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

/**
 * 当前登录用户的完整视图。
 *
 * <p>前端 {@code vue-element-admin} 的 {@code getInfo} action 消费本响应：
 * <ul>
 *   <li>{@code nickname} / {@code avatar} → 写入 Vuex {@code user} 模块，顶栏展示</li>
 *   <li>{@code permissions} → 写入 Vuex {@code user} 模块，供 {@code v-permission} 指令使用</li>
 *   <li>{@code menus} → 写入 Vuex {@code permission} 模块，用于生成动态路由</li>
 * </ul>
 *
 * @param id          用户 ID
 * @param username    用户名
 * @param nickname    昵称
 * @param avatar      头像 URL；可为 {@code null}
 * @param permissions 权限点编码集合
 * @param menus       菜单树（已按用户权限过滤、已组装成树）
 * @author QI Guang
 */
@Schema(name = "ProfileResponse", description = "当前登录用户的完整视图")
public record ProfileResponse(

        @Schema(description = "用户 ID")
        Long id,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "昵称")
        String nickname,

        @Schema(description = "头像 URL")
        String avatar,

        @Schema(description = "权限点编码集合")
        Set<String> permissions,

        @Schema(description = "菜单树")
        List<UserMenuNode> menus
) {
    public ProfileResponse {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        menus = menus == null ? List.of() : List.copyOf(menus);
    }
}
