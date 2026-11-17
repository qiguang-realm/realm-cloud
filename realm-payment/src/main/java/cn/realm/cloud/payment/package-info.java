/**
 * Realm 框架的支付处理模块。
 *
 * <p><b>模块定位：</b>
 * 负责支付单的创建、第三方支付渠道的调用、异步回调的幂等处理、退款以及交易对账。
 * 不承载订单/交易模块的业务逻辑，不包含财务系统的记账逻辑。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>资金精度红线</b>：所有金额字段必须使用最小货币单位（如分）的 {@code Long}
 *         或 {@code BigDecimal}。绝对禁止使用 {@code float} 或 {@code double}，
 *         防止浮点数精度丢失导致资损。</li>
 *     <li><b>防腐层（ACL）隔离</b>：微信、支付宝等第三方支付 SDK 的类型
 *         （如 {@code WxPayService}、{@code AlipayClient}）禁止泄漏到
 *         {@code domain} 和 {@code application} 层。必须通过 {@code PaymentGateway}
 *         接口进行统一适配，确保未来增加新渠道（如银联、PayPal）时核心业务零修改。</li>
 *     <li><b>回调幂等（Idempotency）</b>：第三方渠道的异步回调（Webhook）是
 *         “至少一次（at-least-once）”的语义，同一笔订单可能收到多次通知。
 *         {@code CallbackIdempotentGuard} 必须强制拦截重复处理。同时，
 *         回调处理必须验证签名，防止伪造通知（依据 OWASP 支付安全指南）。</li>
 *     <li><b>独立身份域</b>：本模块不依赖 {@code realm-customer} 和 {@code realm-workforce-identity}。
 *         支付只关心“这笔钱是否成功”，如果需要通知客户，通过发布
 *         {@code PaymentSucceededEvent} 让应用层或消息模块去编排。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Handles payment order creation, third-party channel invocation, idempotent
 * callback processing, refunds, and reconciliation. Does not carry order/trade
 * module business logic or financial bookkeeping. Enforces strict money precision,
 * anti-corruption layers for third-party SDKs, and idempotency for webhooks.
 *
 * @author QI Guang
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "realm-core",
                "realm-utils",
                "realm-audit-logging :: api",        // 退款、渠道配置修改等敏感操作需要审计
                "realm-stability :: api",            // 回调处理的分布式锁与幂等控制
                "realm-messaging :: api",            // 发布 PaymentSucceededEvent 供下游消费
                "realm-web-spring-boot-starter",     // 接收三方回调的 HTTP 接口
                "realm-mybatis-plus-spring-boot-starter" // 持久化支付单、流水
                // 不列 realm-workforce-identity：支付只需要 SubjectContext 中的操作人 ID
                // 不列 realm-customer：支付不关心客户详情，通知通过事件驱动
        }
)
package cn.realm.cloud.payment;
