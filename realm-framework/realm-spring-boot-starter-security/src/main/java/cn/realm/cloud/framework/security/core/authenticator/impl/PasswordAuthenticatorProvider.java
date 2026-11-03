package cn.realm.cloud.framework.security.core.authenticator.impl;

import cn.realm.cloud.framework.security.core.LoginUserProvider;
import cn.realm.cloud.framework.security.core.authenticator.LoginIdentifierType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码认证器（用户名/邮箱/手机号 + 密码）。
 *
 * <p>依据：OWASP 认证备忘单——不区分“用户不存在”与“密码错误”，
 * 防止用户名枚举。密码校验使用 BCrypt。
 *
 * <p><b>为什么三种登录标识共用一个认证器：</b>三者共用同一个
 * {@code iam_user.password_hash}，认证逻辑完全一致，只是查询字段不同。
 * 拆分三个认证器只会在 {@code AuthService} 里增加无意义的分支。
 *
 * @author QI Guang
 */
public class PasswordAuthenticatorProvider {

    private final LoginUserProvider loginUserProvider;
    private final PasswordEncoder passwordEncoder;

    public PasswordAuthenticatorProvider(LoginUserProvider loginUserProvider,
                                         PasswordEncoder passwordEncoder) {
        this.loginUserProvider = loginUserProvider;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 执行密码认证。
     *
     * @param type       登录标识类型
     * @param identifier 登录标识
     * @param password   明文密码
     * @return 认证通过的用户 ID
     * @throws BadCredentialsException 用户不存在或密码错误
     * @throws DisabledException       账号已停用
     */
    public Long authenticate(LoginIdentifierType type, String identifier, String password) {
        LoginUserProvider.LoginCredential credential = loginUserProvider
                .loadCredential(type, identifier)
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (credential.status() == null || credential.status() != 0) {
            throw new DisabledException("账号已停用");
        }
        return credential.id();
    }
}
