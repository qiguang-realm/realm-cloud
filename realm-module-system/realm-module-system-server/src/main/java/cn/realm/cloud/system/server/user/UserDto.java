package cn.realm.cloud.system.server.user;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户只读契约。
 *
 * <p>跨模块传递的用户信息，<b>不包含</b>密码哈希、审计字段、逻辑删除标记等内部字段。
 * 字段变更会影响所有依赖 {@code user} 模块的其他模块（{@code organization}、
 * {@code role}、{@code menu}、{@code auth}），新增字段需评估兼容性，
 * 删除/重命名字段是破坏性变更。
 *
 * <p><b>为什么不含 passwordHash：</b>密码哈希只应在登录流程中使用一次，
 * 不应进入通用的用户信息契约。登录专用的凭据通过
 * {@link UserFacade#findCredential(
 *cn.realm.cloud.framework.security.core.authenticator.LoginIdentifierType, String)}
 * 返回独立的 {@link UserCredentialDto}，与 {@code UserDto} 物理分离。
 *
 * <p><b>为什么含 avatarUrl：</b>{@code /auth/profile} 返回当前用户的完整视图时，
 * 需要头像 URL 供前端顶栏展示。头像是用户基本信息的一部分，不是管理字段，
 * 放在 {@code UserDto} 里由 {@code auth} 模块直接读取，避免为“取头像”
 * 单独开一个 Facade 方法。
 *
 * @param id           用户 ID
 * @param username     用户名
 * @param nickname     昵称
 * @param avatarUrl    头像 URL；可为 {@code null}
 * @param departmentId 所属部门 ID；可为 {@code null}
 * @param status       状态：0-正常、1-停用
 * @param tenantId     租户 ID
 * @author QI Guang
 */
@Schema(name = "UserDto", description = "用户只读契约")
public record UserDto(

        @Schema(description = "用户 ID")
        Long id,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "昵称")
        String nickname,

        @Schema(description = "头像 URL")
        String avatarUrl,

        @Schema(description = "所属部门 ID")
        Long departmentId,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status,

        @Schema(description = "租户 ID")
        Long tenantId
) {
}
