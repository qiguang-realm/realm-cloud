/**
 * Realm 框架的应用层数据范围（行级过滤）自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 基于 MyBatis-Plus 拦截器，在 SQL 解析阶段根据当前调用方的数据范围规则自动追加
 * WHERE 过滤条件（如 {@code dept_id IN (...)} 或永假条件 {@code 1 = 0}），
 * 实现无代码侵入的行级数据权限控制。本模块只消费权限规则，不定义规则；
 * 规则由 {@code realm-workforce-identity} 或应用安全层通过 SPI 提供。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>只做 SQL 织入，不定义规则</b>：本模块的 {@code DataScopeContextResolver}
 *         是唯一的规则来源抽象。规则的具体内容（哪个角色对应哪个 DataScope）属于
 *         Workforce IAM 或 Authorization 的职责，本模块不得硬编码任何角色判断逻辑。</li>
 *     <li><b>默认 fail-close</b>：当上下文缺失、规则非法或 SQL 谓词生成失败时，
 *         默认行为是追加永假条件 {@code 1 = 0}，而非放行全部数据。数据权限属于安全边界，
 *         任何异常都应中断查询，不允许退回未过滤的原始 SQL[reference:4]。</li>
 *     <li><b>不承载租户隔离</b>：租户隔离由 {@code realm-multitenancy} 负责。
 *         本模块只处理行级数据范围（部门、本人、自定义部门集合），两者在拦截器链中
 *         是并列关系，不互相包含。</li>
 *     <li><b>facade + internal 边界</b>：根包下只保留 {@code DataScope}、{@code DataScopeRule}、
 *         {@code DataScopeContext} 三个公共契约，所有拦截器实现、上下文持有者、
 *         SQL 谓词提供器均位于 {@code internal/} 下，由 Spring Modulith 强制隔离。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides row-level data scope filtering via a MyBatis-Plus interceptor. Appends
 * WHERE conditions based on the caller's data scope rules at SQL parse time.
 * Consumes rules only; never defines them. Defaults to fail-close (appends
 * {@code 1 = 0}) on any anomaly rather than falling back to unfiltered SQL.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.biz.datascope;
