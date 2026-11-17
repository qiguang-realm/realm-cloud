package cn.realm.cloud.framework.core.security.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 密码认证令牌。
 *
 * <p><b>命名依据：</b>采用 {@code PasswordAuthenticationToken} 而非 {@code PasswordCredential}
 * 或 {@code UserCredentialDto}。
 * <ul>
 *     <li>{@code Token} 在安全领域天然带有“短期、一次性、高敏感”的语义，
 *         完美契合本类“仅在登录校验时短暂存在，校验后立即丢弃”的生命周期。</li>
 *     <li>命名与 Spring Security 的核心类 {@code UsernamePasswordAuthenticationToken}
 *         保持术语对齐，降低团队的理解成本。</li>
 *     <li>摒弃了 {@code Dto} 这种信息量低的后缀，明确传达了“这是用于认证的载体”。</li>
 * </ul>
 *
 * <p><b>生命周期与安全红线（极其重要）：</b>
 * <ul>
 *     <li>本对象包含 {@code passwordHash}（密码哈希），由业务模块的
 *         {@code PrincipalProvider.loadCredential} 提供给框架。</li>
 *     <li>它<b>只允许</b>在密码校验（如 {@code PasswordEncoder.matches}）时存在。</li>
 *     <li><b>严禁</b>将其放入 {@code SecurityContext}、Session、Redis 或任何长效缓存中。</li>
 *     <li>一旦密码校验完成，该对象引用应立即被丢弃，交由 GC 回收。</li>
 * </ul>
 *
 * <p><b>日志脱敏防御（OWASP ASVS V16）：</b>
 * 本类重写了 {@link #toString()} 方法，对 {@code passwordHash} 进行了完全掩码。
 * 即使开发者不小心将本对象打印到日志（如 {@code log.info("认证请求: {}", token)}），
 * 也不会导致密码哈希泄漏到日志系统中。
 *
 * @param id           用户 ID（主账号）
 * @param username     用户名
 * @param passwordHash 密码哈希（如 BCrypt 格式），登录校验的唯一输入
 * @param status       状态：0-正常，1-停用
 * @param tenantId     租户 ID
 * @author QI Guang
 * @see cn.realm.cloud.framework.core.security.provider.PrincipalProvider
 */
public record PasswordAuthenticationToken(
        Long id,
        String username,
        String passwordHash,
        Integer status,
        Long tenantId
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 重写 {@code toString} 以防御敏感信息泄露。
     *
     * <p>对 {@code passwordHash} 使用 {@code ***} 完全掩码，
     * 确保任何框架调试输出、异常堆栈或未防呆的日志语句都不会记录下真实的密码哈希。
     *
     * @return 脱敏后的对象字符串表示
     */
    @Override
    public String toString() {
        return "PasswordAuthenticationToken{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", passwordHash='***'" +
                ", status=" + status +
                ", tenantId=" + tenantId +
                '}';
    }
}
