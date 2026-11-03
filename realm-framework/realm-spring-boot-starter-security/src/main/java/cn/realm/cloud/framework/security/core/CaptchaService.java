package cn.realm.cloud.framework.security.core;

/**
 * 验证码服务。
 *
 * <p>设计依据：NIST SP 800-63B-4 要求认证端点具备暴力破解防护；
 * OWASP 认证备忘单推荐验证码作为速率限制的补充手段。
 *
 * @author QI Guang
 */
public interface CaptchaService {

    CaptchaResult generate();

    /**
     * 校验验证码；失败时抛 BadCredentialsException。
     */
    void verify(String key, String code);

    /**
     * 验证码结果。
     *
     * @param key         验证码唯一标识（前端回传）
     * @param imageBase64 图片的 base64 data URI
     */
    record CaptchaResult(String key, String imageBase64) {
    }
}
