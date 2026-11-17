/**
 * Realm 框架的可观测性与稳定性保护自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 本模块将“可观测性”（指标、追踪、日志）与“稳定性保护”（限流、熔断、重试、舱壁隔离）
 * 统一为一个自动配置模块。二者共享同一套指标基础设施（Micrometer）与运维消费者，
 * 合并交付可以避免跨模块的指标契约漂移。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>感知与执行分离</b>：{@code observability} 子包只负责采集与暴露，
 *         {@code protection} 子包只负责策略执行，二者通过 {@code integration} 子包解耦。</li>
 *     <li><b>条件装配</b>：所有 {@code @Bean} 必须使用 {@code @ConditionalOnMissingBean} /
 *         {@code @ConditionalOnProperty}，确保应用可以完全覆盖或关闭任何能力。</li>
 *     <li><b>不绑定单一实现</b>：熔断与限流通过 SPI 适配 Resilience4j 或 Sentinel，
 *         核心逻辑不硬编码于某一框架。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * A unified auto-configuration module for observability and stability protection.
 * Observability and protection share the same metrics infrastructure (Micrometer)
 * and operational consumers; delivering them together avoids cross-module metric
 * contract drift.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.stability;
