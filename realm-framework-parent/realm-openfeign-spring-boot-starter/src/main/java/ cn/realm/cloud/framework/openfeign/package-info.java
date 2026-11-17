/**
 * Realm 框架的 OpenFeign 客户端治理自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 为基于 OpenFeign 的出站 HTTP 调用提供统一的治理基线，包括：
 * 幂等性感知的重试策略、realm 上下文（租户/追踪/认证）的自动传播、
 * 可观测性埋点、以及请求/响应日志的敏感数据脱敏。
 * 不重复实现声明式客户端、负载均衡或服务发现（由官方 starter 提供）。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>幂等性重试红线</b>：默认重试策略必须基于 HTTP 方法的幂等性（RFC 9110），
 *         对 POST 等非幂等方法禁用自动重试。任何绕过此红线启用盲目重试的配置都应被拒绝[reference:6]。</li>
 *     <li><b>传播条件化</b>：租户、追踪、认证的传播拦截器必须使用 {@code @ConditionalOnClass}，
 *         在对应上下文缺失时自动退让，不强制所有应用引入 {@code realm-multitenancy}
 *         或 {@code spring-security-core}。</li>
 *     <li><b>脱敏不可绕过</b>：{@code MaskingFeignLogger} 与 {@code MaskingErrorDecoder}
 *         必须在默认路径上生效；应用可以覆盖 Logger 或 ErrorDecoder，但框架默认提供的实现
 *         已剥离敏感信息，不允许在默认路径上泄漏[reference:7]。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides a governance baseline for OpenFeign outbound calls: idempotency-aware retry,
 * automatic realm context (tenant/trace/auth) propagation, observability instrumentation,
 * and sensitive-data masking in request/response logs. Does not re-implement declarative
 * clients, load balancing, or service discovery.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.openfeign;
