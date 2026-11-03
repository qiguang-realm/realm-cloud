package cn.realm.cloud.framework.security.core;

import java.util.Set;

/**
 * 登录用户上下文。
 *
 * <p>设计依据：OWASP ASVS V2 要求认证后不在会话/上下文中保留密码哈希。
 * 本记录刻意不包含 passwordHash，仅保留授权所需的 permissions 集合。
 *
 * @param id          用户 ID
 * @param username    用户名
 * @param nickname    昵称
 * @param status      状态：0-正常，1-停用
 * @param tenantId    租户 ID
 * @param permissions 权限点集合（如 "system:user:create"）
 */
public record LoginUser(
        Long id,
        String username,
        String nickname,
        Integer status,
        Long tenantId,
        Set<String> permissions
) {
    public LoginUser {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
