package cn.realm.cloud.framework.security.core.authenticator;

/**
 * 登录标识类型。
 *
 * <p>对应 {@code iam_user} 表的三个唯一字段：{@code username}、{@code email}、
 * {@code phone_number}。用户可以用其中任意一个 + 密码登录，三者共用同一个
 * {@code password_hash}。
 *
 * <p><b>为什么不是 {@code AuthenticatorType}：</b>“认证器类型”隐含了
 * “每种类型有不同的认证机制”（密码、TOTP、Passkey、OIDC……）。当前
 * 现有表结构只支持“密码”这一种机制，三种标识的差异只在于用哪个字段
 * 查找用户。用 {@code LoginIdentifierType} 更准确地表达这个事实。
 *
 * <p><b>扩展路径：</b>如果未来新增第三方登录，需要引入新的认证器机制，
 * 那时再引入 {@code AuthenticatorType}（PASSWORD / OIDC / PASSKEY），
 * 与 {@code LoginIdentifierType} 组合使用——但那需要新增表来存外部标识，
 * 不属于当前阶段的范围。
 *
 * @author QI Guang
 */
public enum LoginIdentifierType {

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
