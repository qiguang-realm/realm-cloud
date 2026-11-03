package cn.realm.cloud.framework.security.auth;

import cn.realm.cloud.framework.security.auth.dto.CaptchaResponse;
import cn.realm.cloud.framework.security.auth.dto.LoginRequest;
import cn.realm.cloud.framework.security.auth.dto.LoginResponse;
import cn.realm.cloud.framework.security.config.properties.RealmSecurityProperties;
import cn.realm.cloud.framework.security.config.properties.TokenProperties;
import cn.realm.cloud.framework.security.core.CaptchaService;
import cn.realm.cloud.framework.security.core.LoginUser;
import cn.realm.cloud.framework.security.core.LoginUserProvider;
import cn.realm.cloud.framework.security.core.TokenService;
import cn.realm.cloud.framework.security.core.authenticator.impl.PasswordAuthenticatorProvider;
import cn.realm.cloud.framework.security.core.risk.RiskContext;
import cn.realm.cloud.framework.security.core.risk.RiskEvaluator;
import cn.realm.cloud.framework.security.core.risk.RiskScore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

/**
 * 认证服务。
 *
 * <p>当前支持的唯一认证机制是“用户名/邮箱/手机号 + 密码”。三种标识
 * 共用同一个 {@code iam_user.password_hash}，差异只在于查询字段。
 *
 * <p><b>不支持第三方登录 / Passkey / TOTP：</b>这些需要存储外部标识
 * 或密钥，现有表结构没有对应字段。未来扩展时必须新增表。
 *
 * @author QI Guang
 */
@RequiredArgsConstructor
public class AuthService {

    private final PasswordAuthenticatorProvider passwordAuthenticator;
    private final LoginUserProvider loginUserProvider;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final RiskEvaluator riskEvaluator;
    private final RealmSecurityProperties properties;
    private final TokenProperties tokenProperties;

    public LoginResponse login(LoginRequest request) {
        if (properties.getCaptcha().isEnabled()) {
            captchaService.verify(request.captchaKey(), request.captchaCode());
        }

        Long userId = passwordAuthenticator.authenticate(
                request.identifierType(), request.identifier(), request.password());

        LoginUser user = loginUserProvider.loadUserById(userId)
                .orElseThrow(() -> new IllegalStateException("用户不存在"));
        if (user.status() == null || user.status() != 0) {
            throw new DisabledException("账号已停用");
        }

        RiskScore risk = riskEvaluator.evaluate(user.id(), new RiskContext(
                null, null, request.deviceFingerprint(), "/auth/login",
                System.currentTimeMillis(), false, null, null));
        if (risk.requiredAalLevel() > 1) {
            throw new BadCredentialsException(
                    "当前风险等级要求更强的认证方式（暂不支持）: " + risk.reason());
        }

        String accessToken = tokenService.createAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user);
        return new LoginResponse(accessToken, refreshToken,
                tokenProperties.getAccessTokenTtl().toSeconds());
    }

    public LoginResponse refresh(String refreshToken) {
        Long userId = tokenService.rotateRefreshToken(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh Token 无效或已过期"));
        LoginUser user = loginUserProvider.loadUserById(userId)
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
        if (user.status() == null || user.status() != 0) {
            throw new DisabledException("账号已停用");
        }
        String newAccess = tokenService.createAccessToken(user);
        String newRefresh = tokenService.createRefreshToken(user);
        return new LoginResponse(newAccess, newRefresh,
                tokenProperties.getAccessTokenTtl().toSeconds());
    }

    public void logout(String refreshToken) {
        tokenService.revokeRefreshToken(refreshToken);
    }

    public CaptchaResponse generateCaptcha() {
        CaptchaService.CaptchaResult result = captchaService.generate();
        return new CaptchaResponse(result.key(), result.imageBase64());
    }
}
