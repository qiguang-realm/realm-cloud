package cn.realm.cloud.framework.security.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 安全基础配置。
 *
 * @author QI Guang
 */
@Data
@ConfigurationProperties(prefix = "realm.security")
public class RealmSecurityProperties {

    /**
     * 认证端点根路径。
     */
    private String authPath = "/auth";

    /**
     * 全局免认证路径（与 {@code auth.path} 无关）。
     *
     * <p>默认放行 {@code /error}（Spring Boot 错误渲染）和健康检查端点。
     * <b>生产环境应注意：</b>如果启用了完整的 actuator，{@code /actuator/**}
     * 会暴露大量管理端点，应通过防火墙或 {@code management.endpoints.web.exposure}
     * 收窄，而不是依赖 Security 放行。
     */
    private List<String> permitAllPaths = List.of(
            "/error",
            "/actuator/health"
    );

    /**
     * 登录流程中不需要 access token 的子路径（相对于 {@code auth.path}）。
     *
     * <p>默认值覆盖：
     * <ul>
     *   <li>{@code login}：密码/TOTP/短信等认证器入口</li>
     *   <li>{@code captcha}：登录前获取验证码</li>
     *   <li>{@code refresh}：用 refresh token 换 access token</li>
     *   <li>{@code logout}：登出（用 refresh token 标识会话，无需 access token）</li>
     *   <li>{@code oauth2/**}：第三方 OIDC 登录的发起与回调</li>
     *   <li>{@code passkey/authenticate/**}：Passkey 认证（登录流程）</li>
     *   <li>{@code mfa/**}：MFA 升级（登录流程第二步）</li>
     * </ul>
     *
     * <p><b>刻意不放行：</b>{@code profile}（需要已认证）、
     * {@code passkey/register/**}（绑定前需先登录）。
     */
    private List<String> authPermitAll = List.of(
            "login",
            "captcha",
            "refresh",
            "logout",
            "oauth2/**",
            "passkey/authenticate/**",
            "mfa/**"
    );

    /**
     * 验证码开关。
     */
    private Captcha captcha = new Captcha();

    @Data
    public static class Captcha {
        /**
         * 是否启用验证码。生产环境建议启用。
         */
        private boolean enabled = false;
    }
}
