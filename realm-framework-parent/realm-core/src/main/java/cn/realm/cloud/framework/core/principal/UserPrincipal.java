package cn.realm.cloud.framework.core.principal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 已认证的用户主体。
 *
 * <p><b>职责：</b>作为当前请求的 {@code Principal}，承载用户基础信息与权限数据。
 * 实现 {@link UserDetails} 接口，以便与 Spring Security 的认证与授权体系无缝集成。
 *
 * <p><b>设计依据（国际标准与最佳实践）：</b>
 * <ul>
 *     <li><b>OWASP ASVS V2（认证）：</b>认证成功后，绝不在会话或上下文中保留密码哈希。
 *         本记录刻意不包含 {@code passwordHash} 字段。</li>
 *     <li><b>Spring Security 规范：</b>实现 {@link UserDetails} 接口，
 *         使框架能够通过 {@link #getAuthorities()} 统一读取权限，
 *         从而让 {@code @PreAuthorize} 和 {@code AuthorizationManager} 正确工作。</li>
 * </ul>
 *
 * @param id          用户 ID
 * @param username    用户名（登录标识）
 * @param nickname    昵称
 * @param status      状态：0-正常，1-停用
 * @param tenantId    租户 ID
 * @param permissions 权限点集合（如 "system:user:create"）
 * @author QI Guang
 * @see PrincipalUtils
 */
public record UserPrincipal(
        Long id,
        String username,
        String nickname,
        Integer status,
        Long tenantId,
        Set<String> permissions
) implements UserDetails {

    /**
     * 紧凑构造器：对 {@code permissions} 进行空安全处理，防止 NPE。
     */
    public UserPrincipal {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    // ================================================================
    // UserDetails 接口实现
    // ================================================================

    /**
     * 获取该用户的权限集合。
     *
     * <p><b>设计说明：</b>将业务层的字符串权限点（如 {@code "system:user:create"}）
     * 适配为 Spring Security 标准的 {@link GrantedAuthority}。
     * 必须返回非 {@code null} 集合。
     *
     * @return 权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 获取密码。
     *
     * <p><b>安全原则：</b>在基于 JWT 等无状态令牌的认证场景中，密码哈希不应被保留在
     * 内存的 Principal 中。此处返回 {@code null} 是正确且安全的实现。
     *
     * @return 总是返回 {@code null}
     */
    @Override
    public String getPassword() {
        return null;
    }

    /**
     * 获取用户名。
     *
     * @return 用户名
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 账户是否未过期。
     *
     * <p>当前业务模型不支持账户过期，返回 {@code true}。
     * 如后续引入账户有效期，可在此处增加逻辑判断。
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户是否未锁定。
     *
     * <p>当前业务模型将“锁定”与“停用”合并在 {@code status} 中处理，
     * 因此此处返回 {@code true}，状态判断交由 {@link #isEnabled()} 负责。
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证是否未过期。
     *
     * <p>当前业务模型不支持凭证过期（如强制修改密码周期），返回 {@code true}。
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账户是否可用（未停用）。
     *
     * <p><b>业务映射：</b>根据 {@code status} 字段判断。
     * {@code 0} 表示正常（启用），{@code 1} 表示停用（禁用）。
     * 如果用户被停用，Spring Security 将拒绝其访问（通常返回 401 或 403）。
     *
     * @return {@code true} 表示账户可用；{@code false} 表示已停用
     */
    @Override
    public boolean isEnabled() {
        return status != null && status == 0;
    }
}
