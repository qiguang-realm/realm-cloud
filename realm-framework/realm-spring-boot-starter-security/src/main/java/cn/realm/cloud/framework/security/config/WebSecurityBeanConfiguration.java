package cn.realm.cloud.framework.security.config;

import cn.realm.cloud.framework.security.auth.AuthService;
import cn.realm.cloud.framework.security.config.properties.RealmSecurityProperties;
import cn.realm.cloud.framework.security.config.properties.TokenProperties;
import cn.realm.cloud.framework.security.core.CaptchaService;
import cn.realm.cloud.framework.security.core.LoginUserProvider;
import cn.realm.cloud.framework.security.core.PermissionChecker;
import cn.realm.cloud.framework.security.core.TokenService;
import cn.realm.cloud.framework.security.core.authenticator.impl.PasswordAuthenticatorProvider;
import cn.realm.cloud.framework.security.core.impl.JwtTokenServiceImpl;
import cn.realm.cloud.framework.security.core.impl.SimpleCaptchaServiceImpl;
import cn.realm.cloud.framework.security.core.risk.RiskEvaluator;
import cn.realm.cloud.framework.security.core.risk.impl.SimpleRuleRiskEvaluator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全核心 Bean 配置。
 *
 * <p>依据：Spring Security 官方推荐——核心 Bean 使用
 * {@code @ConditionalOnMissingBean}，允许业务方覆盖，保持 starter 的开放性。
 * NIST SP 800-63B-4 要求密码哈希使用自适应单向函数（BCrypt cost≥10）。
 *
 * <p><b>为什么没有多认证器注册表：</b>当前登录机制只有一种——
 * 「用户名 / 邮箱 / 手机号 + 密码」。TOTP / Passkey / OIDC / 微信
 * 需要额外存储（TOTP 密钥、Passkey 公钥、OIDC sub、微信 openid），
 * 当前 {@code iam_user} 表结构不支持，必须新增 {@code iam_user_identity}
 * 之类的关联表才能实现。在新增表之前，不引入 {@code AuthenticatorProvider}
 * 抽象——一个只实现 1/6 的注册表不是“为未来预留”，而是“用虚构的
 * 扩展能力污染当前只有一种机制的事实”。
 *
 * <p><b>为什么 Bean 由本类声明而不是依赖 {@code @Component} 扫描：</b>
 * starter 的包路径 {@code cn.realm.cloud.framework.security} 不在业务应用的
 * {@code @SpringBootApplication} 扫描范围内，{@code @Component} 注解不会生效。
 * 所有 starter 内的 Bean 必须通过本类显式声明。
 *
 * @author QI Guang
 */
@Configuration(proxyBeanMethods = false)
public class WebSecurityBeanConfiguration {

    // ==================== 基础 Bean ====================

    /**
     * 密码编码器。
     *
     * <p>BCrypt cost=10，符合 NIST SP 800-63B-4 对自适应单向函数的要求。
     * 成本参数可根据部署环境的 CPU 能力调整——cost 每 +1，哈希耗时翻倍，
     * 校验耗时同步翻倍。10 在多数服务器上单次约 50-100ms，是安全与
     * 性能的平衡点。
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * 令牌服务。
     *
     * <p>默认实现：JWT Access Token（无状态）+ Redis 或 Guava Cache 存储
     * Refresh Token 状态。依据 RFC 7519（JWT）+ RFC 9700（Refresh Token
     * 轮换与重用检测）。业务方可通过声明自己的 {@code TokenService} Bean
     * 覆盖本实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenService tokenService(TokenProperties properties) {
        return new JwtTokenServiceImpl(properties);
    }

    /**
     * 验证码服务。
     *
     * <p>默认实现：SVG 验证码 + 本地缓存。依据 NIST SP 800-63B-4 要求
     * 认证端点具备暴力破解防护。业务方如使用第三方验证码服务
     * （阿里云、腾讯云等），可通过声明自己的 {@code CaptchaService}
     * Bean 覆盖本实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public CaptchaService captchaService() {
        return new SimpleCaptchaServiceImpl();
    }

    /**
     * 权限检查器，供 {@code @PreAuthorize("@ss.hasPermission('xxx')")} 使用。
     *
     * <p>Bean 名称必须是 {@code "ss"}——业务代码里的 SpEL 表达式
     * {@code @ss.hasPermission(...)} 通过这个名称引用本 Bean。
     * 业务方如需自定义权限判断逻辑（如从 Redis 缓存读取权限点），
     * 可以声明自己的 {@code PermissionChecker} Bean 并保持名称为 {@code "ss"}。
     */
    @Bean("ss")
    @ConditionalOnMissingBean(name = "ss")
    public PermissionChecker permissionChecker() {
        return new PermissionChecker();
    }

    // ==================== 认证器 ====================

    /**
     * 密码认证器（用户名 / 邮箱 / 手机号 + 密码）。
     *
     * <p>三种登录标识共用同一个 {@code iam_user.password_hash}，
     * 认证逻辑完全一致，只是查询字段不同——所以一个认证器覆盖全部。
     *
     * <p><b>为什么没有 {@code AuthenticatorProvider} 接口：</b>
     * 当前只有密码这一种机制。引入接口 + 注册表 + 凭据标记接口
     * 是为 TOTP / Passkey / OIDC 预留的抽象，但那些机制需要新增表
     * 才能实现。等真正需要时再引入抽象，而不是为“可能永远不会来的
     * 认证器”写一套空实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordAuthenticatorProvider passwordAuthenticatorProvider(
            LoginUserProvider loginUserProvider, PasswordEncoder passwordEncoder) {
        return new PasswordAuthenticatorProvider(loginUserProvider, passwordEncoder);
    }

    // ==================== 风险评分 ====================

    /**
     * 情境风险评估器。
     *
     * <p>默认实现：基于规则的简单评分（管理端路径要求 AAL2、未知设备
     * 加分、非常规时段加分等）。依据 NIST SP 800-207 零信任架构——
     * 访问决策应基于多因子动态评估，而非仅依赖一次认证结果。
     *
     * <p>当前实现只做“单一密码认证能否满足本次请求的风险要求”的判断
     * （AAL1 vs AAL2）。由于不支持 MFA，高风险请求会被直接拒绝，
     * 而不是要求追加第二因素。业务方如需更精细的风控策略，可通过
     * 声明自己的 {@code RiskEvaluator} Bean 覆盖本实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public RiskEvaluator riskEvaluator() {
        return new SimpleRuleRiskEvaluator();
    }

    // ==================== 认证服务 ====================

    /**
     * 认证服务。
     *
     * <p>协调密码认证器、令牌服务、验证码服务、风险评估器完成登录流程。
     *
     * <p><b>为什么不再注入 {@code AuthenticatorRegistry} 和 {@code ObjectMapper}：</b>
     * 简化为单一密码认证器后，不再需要按类型路由的注册表；登录请求的
     * 凭据不再用 JSON 传递（{@code identifierType + identifier + password}
     * 三个独立字段），所以也不需要 {@code ObjectMapper} 反序列化。
     *
     * <p><b>关于 {@code securityObjectMapper} Bean：</b>本类不声明它。
     * 如果 starter 的其他组件（如未来的请求体解析）需要，可以单独声明
     * 一个独立的 {@code ObjectMapper}，但不要命名为 {@code "objectMapper"}——
     * 那会与 Spring Boot 的 {@code JacksonAutoConfiguration} 冲突。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthService authService(PasswordAuthenticatorProvider passwordAuthenticator,
                                   LoginUserProvider loginUserProvider,
                                   TokenService tokenService,
                                   CaptchaService captchaService,
                                   RiskEvaluator riskEvaluator,
                                   RealmSecurityProperties properties,
                                   TokenProperties tokenProperties) {
        return new AuthService(passwordAuthenticator, loginUserProvider, tokenService,
                captchaService, riskEvaluator, properties, tokenProperties);
    }
}
