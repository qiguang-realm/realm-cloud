package cn.realm.cloud.workforce.identity.internal.auth.adapter.inbound.web.dto;

import cn.realm.cloud.workforce.identity.internal.auth.domain.model.UserIdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 用户认证请求。
 *
 * <p><b>命名依据：</b>采用 {@code AuthenticationRequest} 而非 {@code LoginRequest}。
 * 前者精确表达了“执行一次身份认证”的系统行为，与 NIST SP 800-63B 及
 * Okta、Oracle 等国际领先平台的 API 术语一致；后者更偏向用户交互动作。
 *
 * <p><b>示例：</b>
 * <pre>
 *   {
 *     "identifierType": "EMAIL",
 *     "identifier": "admin@example.com",
 *     "password": "xxx",
 *     "captchaKey": "uuid-xxx",
 *     "captchaCode": "1234",
 *     "deviceFingerprint": "fp-abc123..."
 *   }
 * </pre>
 *
 * <p><b>安全设计（OWASP ASVS V16）：</b>
 * 本类重写了 {@link #toString()} 方法，对 {@code password}、{@code captchaCode}
 * 等敏感字段进行掩码处理，防止日志打印或框架调试输出导致凭证泄露。
 *
 * <p><b>关于动态验证码的校验：</b>
 * {@code captchaKey} 与 {@code captchaCode} 未在此处强制标注 {@code @NotBlank}，
 * 是因为“是否需要验证码”取决于后端的风控策略（可能按 IP 频率、环境配置动态决定）。
 * 该业务规则由服务层的 {@code LoginService} 负责判断，DTO 保持纯粹的数据载体角色。
 *
 * @param identifierType    登录标识类型（用户名、邮箱、手机号）
 * @param identifier        登录标识值（如 admin@example.com）
 * @param password          明文密码（仅在传输层加密，服务端接收后不可记录）
 * @param captchaKey        验证码 key（后端生成，启用验证码时必填）
 * @param captchaCode       验证码值（用户输入，校验后作废）
 * @param deviceFingerprint 设备指纹（前端计算后回传，用于风控与审计）
 * @author QI Guang
 */
public record AuthenticationRequest(
        @NotNull(message = "登录标识类型不能为空") UserIdentifierType identifierType,

        @NotBlank(message = "登录标识不能为空") String identifier,

        @NotBlank(message = "密码不能为空") String password,

        String captchaKey,

        String captchaCode,

        String deviceFingerprint
) {

    /**
     * 重写 {@code toString} 以防御敏感信息泄露。
     *
     * <p>密码和验证码使用 {@code ***} 完全掩码；设备指纹通常较长，
     * 仅保留前 8 个字符方便排查问题，其余部分用 {@code ***} 代替。
     *
     * @return 脱敏后的对象字符串表示
     */
    @Override
    public String toString() {
        String maskedFingerprint = "***";
        if (deviceFingerprint != null && deviceFingerprint.length() > 8) {
            maskedFingerprint = deviceFingerprint.substring(0, 8) + "***";
        } else if (deviceFingerprint != null) {
            maskedFingerprint = deviceFingerprint; // 过短的指纹直接输出
        }

        return "AuthenticationRequest{" +
                "identifierType=" + identifierType +
                ", identifier='" + identifier + '\'' +
                ", password='***'" +
                ", captchaKey='" + captchaKey + '\'' +
                ", captchaCode='***'" +
                ", deviceFingerprint='" + maskedFingerprint + '\'' +
                '}';
    }
}
