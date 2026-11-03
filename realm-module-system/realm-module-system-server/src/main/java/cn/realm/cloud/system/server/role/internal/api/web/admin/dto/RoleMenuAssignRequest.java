package cn.realm.cloud.system.server.role.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 全量替换角色菜单授权请求。
 *
 * <p>语义是“设置角色拥有这些菜单”，而不是逐条增删：
 * 服务端先删除 {@code iam_role_menu} 中该角色的所有关联，再插入新列表。
 * 这与“逐条 add/remove”的增量接口有本质区别——增量接口需要前端维护
 * “哪些是新增、哪些是移除”，而全量替换只需提交最终状态，前端逻辑更简单，
 * 也避免了并发下“两个请求各自增删一半”的中间态。
 *
 * <p>空列表是合法的——表示清空该角色的所有菜单授权。用
 * {@code @NotNull} 而不是 {@code @NotEmpty}，就是为了允许“清空”这一语义。
 *
 * <p>元素上的 {@code @NotNull} 防止列表中混入 {@code null}，
 * 避免下游写入 {@code iam_role_menu.menu_id = null} 这样的脏数据。
 *
 * <p>与 {@code UserRoleAssignRequest} / {@code UserPositionAssignRequest}
 * 同构：统一用 record 包装 {@code List}，不再接收裸 {@code List<Long>}。
 *
 * @param menuIds 菜单 ID 列表；空列表表示清空该角色的所有菜单授权
 * @author QI Guang
 */
@Schema(name = "RoleMenuAssignRequest", description = "全量替换角色菜单授权请求")
public record RoleMenuAssignRequest(

        @Schema(description = "菜单 ID 列表；空列表表示清空该角色的所有菜单授权")
        @NotNull
        List<@NotNull Long> menuIds
) {
}
