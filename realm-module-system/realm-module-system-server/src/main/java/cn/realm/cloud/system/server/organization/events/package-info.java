/**
 * 组织架构模块对外发布的事件契约。
 *
 * <p>本包中的所有事件是 {@code organization} 模块公开 API 的一部分，
 * 其他模块通过 {@code allowedDependencies} 中的
 * {@code "organization::events"} 依赖，<b>不</b>直接引用
 * {@code organization} 模块的其他类型。
 *
 * <p>本包下的事件均由 {@code organization} 模块在状态变更的事务内发布，
 * 订阅方<b>必须</b>使用 {@code @ApplicationModuleListener}（或至少
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}）消费，
 * <b>禁止</b>使用裸 {@code @EventListener}——后者会在事务提交前同步执行，
 * 删除事件在事务回滚时会导致数据不一致。
 *
 * @author QI Guang
 */
@NamedInterface("events")
package cn.realm.cloud.system.server.organization.events;

import org.springframework.modulith.NamedInterface;
