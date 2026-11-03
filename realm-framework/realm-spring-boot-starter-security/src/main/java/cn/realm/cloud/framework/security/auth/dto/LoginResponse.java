package cn.realm.cloud.framework.security.auth.dto;

/**
 * 登录响应。
 *
 * @param accessToken  Access Token（短期，用于 Authorization: Bearer）
 * @param refreshToken Refresh Token（一次性，用于换新 access token）
 * @param expiresIn    Access Token 剩余有效秒数
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
