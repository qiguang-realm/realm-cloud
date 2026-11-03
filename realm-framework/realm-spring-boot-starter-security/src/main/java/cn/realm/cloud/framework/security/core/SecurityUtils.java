package cn.realm.cloud.framework.security.core;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 安全上下文工具。
 *
 * <p>设计依据：OWASP ASVS V3 要求会话标识符不应通过 URL 传递；
 * 所有身份信息从 SecurityContext 读取，不信任客户端传入的 userId 参数。
 *
 * @author QI Guang
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<LoginUser> findLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public static LoginUser getLoginUser() {
        return findLoginUser()
                .orElseThrow(() -> new IllegalStateException("未登录或登录已过期"));
    }

    public static Long getLoginUserId() {
        return getLoginUser().id();
    }

    public static boolean hasPermission(String permission) {
        return findLoginUser()
                .map(u -> u.permissions().contains(permission))
                .orElse(false);
    }
}
