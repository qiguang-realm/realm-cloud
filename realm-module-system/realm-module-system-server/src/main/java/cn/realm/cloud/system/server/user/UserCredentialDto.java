package cn.realm.cloud.system.server.user;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录凭据 DTO。
 *
 * <p><b>只在登录流程中使用</b>——由 {@code LoginUserProvider.loadCredential}
 * 返回，认证成功后立即丢弃，不缓存、不传递到其他模块。
 *
 * <p><b>为什么必须包含 passwordHash：</b>这是密码认证的唯一输入。
 * {@code PasswordAuthenticatorProvider.authenticate} 拿到本 DTO 后，
 * 用 {@code PasswordEncoder.matches(rawPassword, passwordHash)} 校验密码。
 * 不含密码哈希，认证就无法完成。
 *
 * <p><b>为什么不含 nickname / departmentId / avatarUrl：</b>这些是
 * 登录成功后构造 {@code LoginUser} 时才需要的字段，那条路径走
 * {@code UserFacade.findById} → {@code UserDto}，不走本 DTO。
 * 把“登录校验字段”和“登录后展示字段”混在一个 DTO 里，会让本 DTO
 * 的职责从“登录凭据”膨胀为“用户快照”，也扩大了密码哈希泄漏的暴露面。
 *
 * <p><b>为什么与 {@code UserDto} 物理分离：</b>{@code UserDto} 会通过
 * {@code UserFacade.findById} 被其他模块（{@code role}、{@code menu}、
 * {@code organization}）在展示场景使用；如果 {@code UserDto} 含
 * {@code passwordHash}，任何一个拿到 {@code UserDto} 的模块都可能
 * 把它打印到日志或返回给前端。物理分离是“密码哈希只出现在登录链路”
 * 的结构性保证，不是命名规范。
 *
 * <p>字段变更影响 {@code realm-spring-boot-starter-security} 的登录流程，
 * 需与 starter 的 {@code LoginUserProvider.LoginCredential} 契约同步评估。
 *
 * @param id           主账号 ID
 * @param username     用户名
 * @param passwordHash 密码哈希（BCrypt），登录校验的唯一输入
 * @param status       状态：0-正常、1-停用；登录时校验此字段
 * @param tenantId     租户 ID
 * @author QI Guang
 */
@Schema(name = "UserCredentialDto", description = "登录凭据 DTO")
public record UserCredentialDto(

        @Schema(description = "主账号 ID")
        Long id,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "密码哈希（BCrypt）")
        String passwordHash,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status,

        @Schema(description = "租户 ID")
        Long tenantId
) {
}
