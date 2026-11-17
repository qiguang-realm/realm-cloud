package cn.realm.cloud.framework.core.security.dto; // 建议包名，可按项目结构调整

/**
 * 令牌认证成功后的响应。
 *
 * <p><b>命名依据：</b>采用 {@code TokenResponse} 而非 {@code LoginResponse}。
 * 这与 OAuth 2.0 (RFC 6749) 及 OpenID Connect 等国际标准中的术语一致，
 * 并广泛用于 Google、Keycloak 等主流平台的 API 中。
 *
 * <p><b>安全设计（OWASP ASVS V16）：</b>
 * 本类重写了 {@link #toString()} 方法，对 {@code accessToken} 和
 * {@code refreshToken} 进行掩码处理，防止令牌在日志打印或框架调试输出时泄露[reference:11]。
 *
 * @param accessToken  Access Token（短期，用于 Authorization: Bearer）
 * @param refreshToken Refresh Token（一次性，用于换新 access token）
 * @param expiresIn    Access Token 剩余有效秒数
 * @author QI Guang
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {

    /**
     * 重写 {@code toString} 以防御敏感信息泄露。
     *
     * <p>对 {@code accessToken} 和 {@code refreshToken} 使用 {@code ***} 完全掩码，
     * 确保任何日志或调试输出都不会记录下真实的令牌值。
     *
     * @return 脱敏后的对象字符串表示
     */
    @Override
    public String toString() {
        return "TokenResponse{" +
                "accessToken='***'" +
                ", refreshToken='***'" +
                ", expiresIn=" + expiresIn +
                '}';
    }
}
