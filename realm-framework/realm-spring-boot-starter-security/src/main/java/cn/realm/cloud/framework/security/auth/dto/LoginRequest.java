package cn.realm.cloud.framework.security.auth.dto;

import cn.realm.cloud.framework.security.core.authenticator.LoginIdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 登录请求。
 *
 * <p>示例：
 * <pre>
 *   {"identifierType": "EMAIL", "identifier": "admin@example.com", "password": "xxx"}
 * </pre>
 *
 * @param identifierType    登录标识类型：USERNAME / EMAIL / PHONE
 * @param identifier        登录标识值
 * @param password          明文密码
 * @param captchaKey        验证码 key（启用验证码时必填）
 * @param captchaCode       验证码值
 * @param deviceFingerprint 设备指纹（前端计算后回传，用于风险评分）
 * @author QI Guang
 */
public record LoginRequest(
        @NotNull LoginIdentifierType identifierType,
        @NotBlank String identifier,
        @NotBlank String password,
        String captchaKey,
        String captchaCode,
        String deviceFingerprint
) {
}
