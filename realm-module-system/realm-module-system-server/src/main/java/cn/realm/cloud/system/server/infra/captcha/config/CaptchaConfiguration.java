package cn.realm.cloud.system.server.infra.captcha.config;

import cn.realm.cloud.system.server.infra.captcha.RedisCaptchaServiceImpl;
import com.anji.captcha.properties.AjCaptchaProperties;
import com.anji.captcha.service.CaptchaCacheService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * AJ-Captcha 验证码缓存配置
 * <p>
 * 所有业务参数（水印、过期时间、频率限制等）均在 application.yml 中配置，
 * 此类仅负责将 Redis 缓存实现注入 Spring 容器。
 *
 * @author QI Guang
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AjCaptchaProperties.class) // 加载配置属性 Bean
//@ImportAutoConfiguration(AjCaptchaAutoConfiguration.class) // 目的：解决 aj-captcha 针对 SpringBoot 3.X 自动配置不生效的问题
public class CaptchaConfiguration {

    @Bean
    @Primary
    public CaptchaCacheService ajCaptchaCacheService(StringRedisTemplate stringRedisTemplate) {
        return new RedisCaptchaServiceImpl(stringRedisTemplate);
    }

    // 删除 ajCaptchaProperties() Bean，让 YAML 配置完全生效
}
