package cn.realm.cloud.framework.security.core.constant;

/**
 * 安全常量。
 *
 * <p>依据：RFC 6750 规定 Bearer Token 通过 Authorization 头传递，格式为 "Bearer &lt;token&gt;"。
 *
 * @author QI Guang
 */
public final class SecurityConstants {

    public static final String TOKEN_HEADER = "Authorization";

    public static final String TOKEN_PREFIX = "Bearer ";

    public static final String STATUS_NORMAL = "0";

    private SecurityConstants() {
    }
}
