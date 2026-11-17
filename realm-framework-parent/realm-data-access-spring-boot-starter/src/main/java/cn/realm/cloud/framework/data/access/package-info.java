/**
 * Realm 框架的数据访问基础设施自动配置模块（ORM 无关）。
 *
 * <p><b>模块定位：</b>
 * 在 Spring Boot 的 {@code DataSourceAutoConfiguration} 之上，提供生产就绪的多数据源路由、
 * 连接池默认值以及事务管理器的“基础定型”。它不包含任何 ORM 适配（MyBatis-Plus 与 JPA
 * 是平行的适配器模块），只负责让上层 ORM 模块能够安全地在 {@code DataSource} 与
 * {@code PlatformTransactionManager} 之上构建。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>事务管理器可退让</b>：默认注册的 {@code DataSourceTransactionManager}
 *         必须标注 {@code @ConditionalOnMissingBean(PlatformTransactionManager.class)}。
 *         这是 JPA 等 ORM 模块能够通过 {@code @AutoConfigureBefore} 接管事务管理器的前提。
 *         如果在此处硬编码事务管理器，JPA 的 {@code JpaTransactionManager} 将因条件不满足
 *         而无法注册，导致 {@code @Transactional} 与 {@code EntityManager} 生命周期脱节[reference:2]。</li>
 *     <li><b>多数据源路由不承载业务语义</b>：{@code DataSourceContextHolder} 只负责
 *         线程级的路由键传递，不解析任何业务规则（如“根据租户选择数据源”）。路由键的
 *         来源（租户上下文、注解、请求头）由 {@code realm-multitenancy} 或应用层决定。</li>
 *     <li><b>连接池默认值安全</b>：Spring Boot 3 将 HikariCP 深度绑定进自动配置链[reference:3]。
 *         本模块不重复引入其他连接池（Druid 等）作为 compile 依赖，避免条件竞争导致
 *         自动配置失效或参数不生效[reference:4]。Druid 等替代方案应以 {@code optional} 引入，
 *         由应用显式选择。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides production-ready multi-datasource routing, connection pool defaults, and
 * foundational transaction manager finalization on top of Spring Boot's
 * {@code DataSourceAutoConfiguration}. Contains no ORM adapters; ORM modules build on
 * top of the {@code DataSource} and {@code PlatformTransactionManager} it provides.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.data.access;
