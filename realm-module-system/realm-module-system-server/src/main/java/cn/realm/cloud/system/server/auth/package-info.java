/**
 * 登录编排模块。
 *
 * <p>负责当前登录用户的资料视图聚合（{@code /auth/profile}），
 * 以及未来的第三方登录、Passkey、MFA 等需要第三方 SDK 的登录方式。
 * 不承载业务数据。
 *
 * <p>依赖：{@code user}（用户信息 + 角色 ID + 权限点）、
 * {@code role}（角色 → 菜单 ID）、{@code menu}（菜单详情 + 菜单树）。
 *
 * @author QI Guang
 */
@ApplicationModule(
        allowedDependencies = {"user", "role", "menu", "shared"}
)
package cn.realm.cloud.system.server.auth;

import org.springframework.modulith.ApplicationModule;
