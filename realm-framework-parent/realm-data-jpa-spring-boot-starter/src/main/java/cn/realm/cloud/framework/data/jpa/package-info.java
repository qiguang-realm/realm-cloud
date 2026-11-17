/**
 * Realm 框架的 Spring Data JPA 适配与定型自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 建立在 {@code realm-data-access} 提供的 {@code DataSource} 之上，负责 JPA 事务管理器的定型、
 * Repository 扫描的默认约定，以及审计基础设施的可选接入。不重新实现
 * {@code EntityManagerFactory} 的创建，不承载业务实体或 Repository 的业务方法。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>多数据源主动退避</b>：自动配置类必须标注
 *         {@code @ConditionalOnSingleCandidate(DataSource.class)}。多数据源场景下，
 *         JPA 自动配置静默退让，由应用显式配置每个数据源的 {@code EntityManagerFactory}
 *         与 {@code JpaTransactionManager}，避免歧义注入。</li>
 *     <li><b>事务管理器定型优先于 JDBC 默认</b>：{@code RealmJpaTransactionAutoConfiguration}
 *         必须通过 {@code @AutoConfigureBefore} 确保在 {@code realm-data-access} 的默认
 *         {@code DataSourceTransactionManager} 之前注册 {@code JpaTransactionManager}。
 *         否则 {@code @Transactional} 与 JPA 的 {@code EntityManager} 生命周期无法对齐。</li>
 *     <li><b>审计身份来源中立</b>：{@code RealmAuditorAware} 的默认实现必须从
 *         {@code SubjectContext} 读取当前操作人，禁止直接依赖 {@code SecurityContextHolder}。
 *         在调度任务、消息消费等无 HTTP 上下文的场景中，{@code SecurityContextHolder}
 *         为空会导致审计字段退化为 {@code system}。</li>
 *     <li><b>审计默认关闭</b>：{@code @EnableJpaAuditing} 必须通过
 *         {@code @ConditionalOnProperty(prefix = "realm.data.jpa.auditing", name = "enabled", matchIfMissing = false)}
 *         控制，默认不启用。避免所有 JPA 应用被动注册审计监听器。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Builds on the {@code DataSource} provided by {@code realm-data-access} to finalize
 * the JPA transaction manager, provide default Repository scanning conventions, and
 * optionally wire auditing infrastructure. Does not re-implement
 * {@code EntityManagerFactory} creation or carry business entities.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.data.jpa;
