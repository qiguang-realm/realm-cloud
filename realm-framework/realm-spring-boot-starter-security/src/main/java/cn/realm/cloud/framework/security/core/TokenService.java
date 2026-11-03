package cn.realm.cloud.framework.security.core;

import java.util.Optional;

/**
 * 令牌服务。
 *
 * <p>设计依据：RFC 9700（OAuth 2.0 安全最佳实践）要求 Refresh Token 轮换
 * 并检测重用。接口设计包含 rotateRefreshToken 和 revokeRefreshToken，
 * 从契约层面强制实现方处理令牌生命周期。
 *
 * @author QI Guang
 */
public interface TokenService {

    String createAccessToken(LoginUser user);

    String createRefreshToken(LoginUser user);

    /**
     * 解析 access token 并返回 userId；无效或过期时返回 empty。
     */
    Optional<Long> parseAccessToken(String token);

    /**
     * 轮换 refresh token：校验后立即作废旧值，返回 userId。
     * 调用方需为新 refresh token 重新走 createRefreshToken。
     */
    Optional<Long> rotateRefreshToken(String refreshToken);

    /**
     * 撤销 refresh token（登出时调用）。
     */
    void revokeRefreshToken(String refreshToken);
}
