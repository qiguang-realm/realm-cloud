package cn.realm.cloud.workforce.identity.internal.auth.domain.model;

/**
 * 用户登录标识符类型。
 *
 * <p>对应用户表三个唯一字段：{@code username}、{@code email}、{@code phone_number}。
 * 用户可用其中任意一个 + 密码登录。
 *
 * <p><b>为什么不是 {@code AuthenticatorType}：</b>根据 NIST SP 800-63B，
 * “认证器（Authenticator）”指的是用于证明身份的工具或机制（如密码、TOTP、Passkey），
 * 而 {@code USERNAME/EMAIL/PHONE} 是用户用来“标识自己是谁”的标识符（Identifier），
 * 两者在认证模型中属于不同概念。
 *
 * <p><b>命名参考：</b>Auth0 的公开 API 使用 {@code IdentifierType}，
 * 枚举值为 {@code "email" | "phone" | "username"}，与本枚举语义一致。
 *
 * <p><b>扩展路径：</b>未来若引入第三方登录（OIDC）或 Passkey，
 * 应新增 {@code AuthenticatorType}（PASSWORD / OIDC / PASSKEY）与此枚举组合使用，
 * 而不是扩展本枚举——因为那属于不同的认证机制，而非不同的用户标识符。
 *
 * @author QI Guang
 */
public enum UserIdentifierType {

    /**
     * 用户名
     */
    USERNAME,

    /**
     * 邮箱
     */
    EMAIL,

    /**
     * 手机号
     */
    PHONE
}
