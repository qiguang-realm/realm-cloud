/**
 * Realm 框架的 WebSocket 连接治理与跨节点中继自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 为 Servlet Web 应用提供本地 WebSocket 连接的生命周期治理，以及基于
 * “广播 + 本地匹配”模型的跨节点消息投递。不解析业务消息语义，不维护
 * 全局 Session 注册表（{@code WebSocketSession} 不可序列化，这是硬约束）。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>Session 不跨节点</b>：{@code SessionRegistry} 只持有本地
 *         {@code ConcurrentHashMap}，Redis 仅作为中继通道，绝不存储 Session 实例。</li>
 *     <li><b>中继 SPI 可替换</b>：核心只依赖 {@code MessageBus} 接口，
 *         Redis / InMemory 实现通过条件装配注入，不硬编码于核心。</li>
 *     <li><b>不承载业务语义</b>：{@code RelayEnvelope.payload} 原样透传，
 *         不解析业务消息类型，业务处理由应用层自行完成。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides local WebSocket connection lifecycle governance and cross-node
 * message delivery via a "broadcast + local matching" model. Business message
 * semantics are never parsed here; sessions are never stored in Redis
 * (WebSocketSession is non-serializable, a hard constraint).
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.websocket;
