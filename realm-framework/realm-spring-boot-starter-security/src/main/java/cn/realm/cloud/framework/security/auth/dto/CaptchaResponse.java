package cn.realm.cloud.framework.security.auth.dto;

/**
 * 验证码响应。
 *
 * @param key   验证码唯一标识
 * @param image 图片 data URI
 */
public record CaptchaResponse(
        String key,
        String image
) {
}
