/**
 * Realm 框架的只读分析上下文（Read-only Analytics Context）。
 *
 * <p><b>模块定位：</b>
 * 本模块是典型的 CQRS（命令查询职责分离）查询端（Query Side）。通过订阅其他业务模块
 * 发布的领域事件（Domain Events），构建专门用于复杂聚合查询的“读模型（Read Model）”。
 * 它只负责报表、仪表盘、数据投影的生成与查询，不承载任何业务主流程的写操作。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>绝对不被业务模块反向依赖</b>：Analytics 是架构中的叶子节点。其他业务模块
 *         只能发布事件供本模块消费，绝不能直接调用本模块的查询 API 或依赖本模块的类。
 *         如果业务逻辑需要统计结果，应由前端发起请求。</li>
 *     <li><b>只读（Read-Only）</b>：本模块只操作 {@code analytics_*} 前缀的读模型表，
 *         绝不直接修改其他业务模块的写模型（如 {@code iam_user}、{@code member_user}）。
 *         任何跨模块的直接写操作都会导致严重的数据一致性灾难。</li>
 *     <li><b>禁止跨域直接查询写表</b>：本模块的数据来源必须是消费其他模块的事件
 *         （如 {@code realm-customer :: events}），或者显式通过只读副本（Read Replica）
 *         查询。绝不能直接写 SQL {@code SELECT * FROM member_user}，这会与业务写操作
 *         竞争数据库资源，且破坏模块边界。</li>
 *     <li><b>不包含复杂事务</b>：由于是只读模型，本模块不需要分布式事务。事件消费与
 *         读模型更新应尽量保持幂等（Idempotent），以应对消息重试。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * A typical CQRS Query Side. Builds read models for complex aggregate queries by
 * subscribing to domain events published by other business modules. Contains only
 * reporting and projection logic, with no write operations for core business flows.
 * It is a leaf node and is never reverse-depended upon by other modules.
 *
 * @author QI Guang
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "realm-core",                        // 基础契约（分页、异常）
                "realm-audit-logging :: api",        // 审计注解（可选）
                "realm-workforce-identity :: events", // 仅消费事件，不调用 API
                "realm-customer :: events",           // 仅消费事件，不调用 API
                "realm-payment :: events"             // 仅消费事件，不调用 API
                // 绝不依赖 realm-workforce-identity-server、realm-customer-server 等实现模块
                // 绝不依赖 realm-authorization（Analytics 不需要授权决策）
        }
)
package cn.realm.cloud.analytics;
