package cn.realm.cloud.framework.security.core.impl;

import cn.hutool.jwt.JWT;
import cn.realm.cloud.framework.security.config.properties.TokenProperties;
import cn.realm.cloud.framework.security.core.LoginUser;
import cn.realm.cloud.framework.security.core.TokenService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * 默认令牌实现：JWT Access Token + Guava Cache 存储 Refresh Token 状态。
 *
 * <p>依据：RFC 7519（JWT）+ RFC 9700（Refresh Token 轮换与重用检测）。
 *
 * <p>生产环境建议用 Redis 替换 Guava Cache，可通过 {@code @ConditionalOnMissingBean}
 * 覆盖本实现。并发轮换场景下 Guava Cache 不保证原子性，业务方可在外层用分布式锁补齐。
 *
 * @author QI Guang
 */
public class JwtTokenServiceImpl implements TokenService {

    private static final String PAYLOAD_USER_ID = "uid";
    private static final String PAYLOAD_USERNAME = "username";
    private static final String PAYLOAD_TYPE = "type";
    private static final String TYPE_ACCESS = "access";

    private final TokenProperties properties;
    private final Cache<String, Long> refreshTokenStore;

    public JwtTokenServiceImpl(TokenProperties properties) {
        this.properties = properties;
        this.refreshTokenStore = CacheBuilder.newBuilder()
                .expireAfterWrite(properties.getRefreshTokenTtl())
                .maximumSize(100_000)
                .build();
    }

    @Override
    public String createAccessToken(LoginUser user) {
        long now = System.currentTimeMillis();
        return JWT.create()
                .setPayload(PAYLOAD_USER_ID, user.id())
                .setPayload(PAYLOAD_USERNAME, user.username())
                .setPayload(PAYLOAD_TYPE, TYPE_ACCESS)
                .setIssuedAt(new Date(now))
                .setExpiresAt(new Date(now + properties.getAccessTokenTtl().toMillis()))
                .setKey(properties.getSecret().getBytes(StandardCharsets.UTF_8))
                .sign();
    }

    @Override
    public String createRefreshToken(LoginUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        refreshTokenStore.put(token, user.id());
        return token;
    }

    @Override
    public Optional<Long> parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            JWT jwt = JWT.of(token);
            if (!jwt.setKey(properties.getSecret().getBytes(StandardCharsets.UTF_8)).verify()) {
                return Optional.empty();
            }
            if (!TYPE_ACCESS.equals(jwt.getPayload(PAYLOAD_TYPE))) {
                return Optional.empty();
            }
            Object uid = jwt.getPayload(PAYLOAD_USER_ID);
            return uid == null ? Optional.empty() : Optional.of(Long.valueOf(uid.toString()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Long> rotateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        Long userId = refreshTokenStore.getIfPresent(refreshToken);
        if (userId == null) {
            return Optional.empty();
        }
        refreshTokenStore.invalidate(refreshToken);
        return Optional.of(userId);
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken != null) {
            refreshTokenStore.invalidate(refreshToken);
        }
    }
}
