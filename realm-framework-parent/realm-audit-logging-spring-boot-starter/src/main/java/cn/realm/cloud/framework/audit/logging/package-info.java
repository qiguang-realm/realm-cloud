/**
 * Realm 框架的操作审计记录自动配置模块（NIST SP 800-53 AU 控制族）。
 *
 * <p><b>模块定位：</b>
 * 为业务方法提供无侵入的审计捕获能力：通过 {@code @AuditLog} 注解标记审计点，
 * AOP 切面捕获“谁（主体）在什么时间对什么（资源）做了什么（动作），结果如何”，
 * 异步写入独立的审计通道。不包含授权决策（AC 控制族），不包含审计查询/分析界面。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>不依赖授权模块</b>：本模块的 {@code AuditSubjectResolver} 必须从
 *         {@code SubjectContext}（realm-core）获取操作人，禁止 import
 *         {@code realm-authorization} 或 {@code SecurityContextHolder}。
 *         授权与审计在控制族层面是分离的（NIST AC vs AU）。</li>
 *     <li><b>异步非阻塞</b>：审计写入必须通过独立的线程池异步执行，禁止在业务线程中
 *         同步写库或写远程 SIEM。线程池的拒绝策略必须是 {@code CallerRunsPolicy}
 *         或等价的“不丢事件”策略，禁止 {@code DiscardPolicy}。</li>
 *     <li><b>审计通道独立于业务数据库</b>：默认实现 {@code Slf4jAuditEventRepository}
 *         将审计记录写入独立的 logger，而不是业务数据库。当应用选择
 *         {@code JdbcAuditEventRepository} 时，必须使用独立的数据库账户或独立的表，
 *         业务代码路径不得直接修改或删除审计记录（append-only）。</li>
 *     <li><b>不承载业务语义</b>：{@code AuditEvent} 只携带操作元数据（主体、动作、
 *         资源、时间、结果、traceId），不解析业务对象的完整业务含义。</li>
 *     <li><b>防篡改是可选增强</b>：{@code HashChain} 提供前序哈希链接，用于检测
 *         审计记录的意外损坏或简单编辑；它不替代外部的不可变存储（如 WORM 存储、
 *         独立 SIEM），后者属于运维层面的职责。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides non-intrusive operational audit logging via {@code @AuditLog} and AOP.
 * Captures "who did what, to what, and with what outcome" and writes asynchronously
 * to an independent audit channel. Contains no authorization decisions (AC family)
 * and no audit query/analysis UI.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.audit.logging;
