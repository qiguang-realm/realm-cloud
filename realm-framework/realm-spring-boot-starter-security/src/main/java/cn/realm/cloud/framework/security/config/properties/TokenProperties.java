package cn.realm.cloud.framework.security.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 令牌配置。
 *
 * <p>依据：RFC 9700——Access Token 必须短生命周期（分钟级）。
 * 通过 externalized config 控制 TTL，禁止硬编码 24 小时以上的过期时间。
 *
 * @author QI Guang
 */
@Data
@ConfigurationProperties(prefix = "realm.security.token")
public class TokenProperties {

    /**
     * HMAC 签名密钥。生产环境必须通过环境变量或配置中心覆盖。
     */
    private String secret = "realm-cloud-default-secret-change-me-in-production";

    /**
     * Access Token 有效期（默认 15 分钟）。
     */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /**
     * Refresh Token 有效期（默认 7 天）。
     */
    private Duration refreshTokenTtl = Duration.ofDays(7);
}
