/**
 * Realm 框架的纯技术契约核心模块。
 * <p>
 * 本模块是整个架构的基石，定义了所有业务模块和框架组件之间通信的“共同语言”。
 * 它只包含不可变的数据结构（DTO）、标准的异常类型、通用枚举以及中立的上下文抽象。
 * <p>
 * <b>架构红线（由 ArchUnit 强制拦截）：</b>
 * <ul>
 *     <li>零业务语义：绝不包含任何与具体业务（如用户、订单）相关的类。</li>
 *     <li>零工具类：绝不包含 StringUtils、DateUtils 等（请使用 realm-utils）。</li>
 *     <li>零框架绑定：除 Jackson 和 Swagger 注解外，禁止引入 Web、ORM、Redis 等依赖。</li>
 * </ul>
 */
package cn.realm.cloud.framework.core;
