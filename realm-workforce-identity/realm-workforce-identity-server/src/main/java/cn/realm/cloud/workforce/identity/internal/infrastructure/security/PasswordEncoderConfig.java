package cn.realm.cloud.workforce.identity.internal.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器基础设施配置
 * 供 user、oauth 子域共用：用户密码存储、登录密码校验
 *
 * @author QI Guang
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 标准密码哈希
        return new BCryptPasswordEncoder();
    }
}
