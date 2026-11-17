package cn.realm.cloud.framework.core.security.provider;

import cn.realm.cloud.framework.core.principal.UserPrincipal;
import cn.realm.cloud.framework.core.security.dto.PasswordAuthenticationToken;

import java.util.Optional;

/**
 * 安全主体提供者（SPI）。
 *
 * <p><b>设计依据：</b>依赖倒置原则（DIP）。框架层（realm-core）定义契约，
 * 业务模块（如 realm-workforce-identity）提供实现。
 *
 * <p><b>架构定位：</b>服务于<b>认证（Authentication）</b>流程。
 * <ul>
 *     <li>登录时：通过 {@link #loadCredential} 加载凭证（含密码哈希）。</li>
 *     <li>Token 校验时：通过 {@link #loadUserById} 加载主体（含权限点）。</li>
 * </ul>
 *
 * @author QI Guang
 */
public interface PrincipalProvider {

    /**
     * 按登录标识加载认证令牌（仅登录时调用）。
     *
     * <p><b>安全提醒：</b>返回的 {@link PasswordAuthenticationToken} 包含密码哈希，
     * 只能在内存中用于密码比对，<b>绝不允许</b>放入 SecurityContext 或任何缓存。
     *
     * @param identifierType 登录标识类型（如 "USERNAME", "EMAIL", "PHONE"）
     * @param identifier     登录标识值
     * @return 认证令牌；用户不存在时返回 empty
     */
    Optional<PasswordAuthenticationToken> loadCredential(String identifierType, String identifier);

    /**
     * 按用户 ID 加载安全主体（每次请求校验 Token 时调用）。
     *
     * @param userId 用户 ID
     * @return 安全主体；用户不存在或已停用时返回 empty
     */
    Optional<UserPrincipal> loadUserById(Long userId);
}
