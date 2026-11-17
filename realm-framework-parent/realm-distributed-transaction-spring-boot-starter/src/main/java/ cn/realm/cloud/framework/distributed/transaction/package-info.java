/**
 * Realm 框架的分布式事务接入与协调自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 建立在 {@code realm-data-access} 之上，负责 Seata 客户端的条件化装配、
 * 多数据源场景下的 {@code DataSourceProxy} 协调，以及事务管理器的定型。
 * 不包含 TC（事务协调器）服务端，不替应用决定 AT/TCC/Saga 事务模式。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>不强制开启自动代理</b>：{@code seata.enable-auto-data-source-proxy} 的默认值
 *         必须为 {@code false}。多数据源场景下，自动代理可能只包装默认数据源或产生
 *         不可预期的行为；代理策略由 {@code DataSourceProxyAutoConfiguration} 显式协调，
 *         应用可通过配置决定哪些数据源参与全局事务[reference:1]。</li>
 *     <li><b>事务管理器定型优先于 JDBC 默认</b>：当类路径存在 JPA 时，本模块必须确保
 *         {@code JpaTransactionManager} 在 {@code realm-data-access} 的默认
 *         {@code DataSourceTransactionManager} 之前注册，否则 JPA 的 {@code EntityManager}
 *         生命周期与全局事务无法对齐。</li>
 *     <li><b>数据源代理目标正确</b>：在多数据源（{@code dynamic-datasource}）场景下，
 *         代理的目标必须是实际执行 SQL 的 {@code DataSource}，而不是
 *         {@code DynamicRoutingDataSource} 本身；否则 Seata 的 RM 无法拦截 SQL，
 *         {@code undo_log} 不会写入，事务形同虚设[reference:2][reference:3]。</li>
 *     <li><b>不承载业务事务逻辑</b>：{@code @GlobalTransactional} 的使用、TCC 接口实现、
 *         Saga 状态机定义均属于应用层职责，本模块不提供任何业务事务模板。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides conditional assembly of the Seata client, coordination of
 * {@code DataSourceProxy} in multi-datasource scenarios, and finalization of the
 * transaction manager. Does not include the TC server; does not decide the
 * AT/TCC/Saga mode on behalf of the application.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.distributed.transaction;
