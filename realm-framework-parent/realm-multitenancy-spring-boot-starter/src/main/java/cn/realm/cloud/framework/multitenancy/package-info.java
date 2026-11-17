/**
 * Realm 框架的租户上下文生命周期管理自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 为 SaaS 应用提供租户上下文的解析、传播与清理能力。仅确定“当前租户是谁”，
 * 不决定“数据如何隔离”。隔离模型（共享表 / 独立 Schema / 独立库）由应用通过
 * {@link cn.realm.cloud.framework.multitenancy.spi.TenantIsolationStrategy} SPI 注入。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>核心零隔离依赖</b>：{@code context} 包禁止 import 任何 DataSource、MyBatis、
 *         Redis 的类型。数据源路由由 {@code realm-data-access} 负责，本模块不参与。</li>
 *     <li><b>解析 SPI 可替换</b>：{@code TenantResolver} 是唯一的租户来源抽象。
 *         默认提供基于请求头的实现，应用可通过 {@code @ConditionalOnMissingBean} 覆盖。</li>
 *     <li><b>请求边界清理</b>：{@code TenantContextFilter} 必须在 {@code finally} 块中
 *         调用 {@code TenantContextHolder.clear()}，防止线程池复用导致上下文泄漏。</li>
 *     <li><b>异步传播可选</b>：跨线程传播依赖 {@code TransmittableThreadLocal}，
 *         以 {@code optional} 依赖引入，应用不引入 TTL 时退化为普通 {@code ThreadLocal}。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides tenant context lifecycle management (resolution, propagation, cleanup)
 * for SaaS applications. Determines "who the tenant is", not "how data is isolated".
 * Isolation models are injected by the application via the {@code TenantIsolationStrategy} SPI.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.multitenancy;
