package cn.realm.cloud.framework.security.core;

import cn.realm.cloud.framework.security.core.authenticator.LoginIdentifierType;

import java.util.Optional;

/**
 * 登录用户数据提供者（SPI）。
 *
 * <p>依赖反转：starter 定义接口，业务模块提供实现。
 *
 * @author QI Guang
 */
public interface LoginUserProvider {

    /**
     * 按登录标识加载登录凭证（含密码哈希）。仅登录时调用一次。
     *
     * <p>实现根据 {@code type} 选择查询字段：
     * <ul>
     *   <li>{@link LoginIdentifierType#USERNAME} → 按 {@code iam_user.username} 查</li>
     *   <li>{@link LoginIdentifierType#EMAIL} → 按 {@code iam_user.email} 查</li>
     *   <li>{@link LoginIdentifierType#PHONE} → 按 {@code iam_user.phone_number} 查</li>
     * </ul>
     *
     * @param type       登录标识类型；为 {@code null} 时返回 empty
     * @param identifier 登录标识；为 {@code null} 或空白时返回 empty
     * @return 登录凭证；用户不存在时返回 empty
     */
    Optional<LoginCredential> loadCredential(LoginIdentifierType type, String identifier);

    /**
     * 按用户 ID 加载登录用户（含权限点）。供 TokenAuthenticationFilter 每次请求使用。
     */
    Optional<LoginUser> loadUserById(Long userId);

    /**
     * 登录凭证：只在登录流程中使用，成功后立即丢弃。
     */
    record LoginCredential(
            Long id,
            String username,
            String passwordHash,
            Integer status,
            Long tenantId
    ) {
    }
}
