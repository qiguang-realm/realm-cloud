/**
 * 菜单模块对外事件契约。
 *
 * <p>用 {@code @NamedInterface("events")} 把本子包暴露为命名接口：其他模块在
 * {@code @ApplicationModule(allowedDependencies = {"menu::events"})} 中声明依赖后，
 * 只能引用本包中的事件类型，无法触达 {@code menu} 根包的其他类型或 {@code internal}。
 *
 * <p><b>当前对外事件：</b>
 * <ul>
 *   <li>{@link cn.realm.cloud.system.server.menu.events.MenuDeletedEvent}：
 *       菜单删除后发布，供 {@code role} 模块清理 {@code iam_role_menu} 残留关联。
 *       这是 {@code menu} 不反向依赖 {@code role} 的前提下，删除菜单时保持
 *       「角色-菜单」关联最终一致的唯一路径。</li>
 *   <li>{@link cn.realm.cloud.system.server.menu.events.MenuStatusChangedEvent}：
 *       菜单状态变更后发布，供运行时权限缓存失效使用（如存在权限缓存）。</li>
 * </ul>
 *
 * <p><b>订阅方必须使用 {@code @ApplicationModuleListener}：</b>
 * 本包下所有事件都在 {@code MenuServiceImpl} 的事务内发布，订阅方
 * <b>禁止</b>使用裸 {@code @EventListener}。原因分两层：
 * <ul>
 *   <li><b>事务时序：</b>{@code @ApplicationModuleListener} 组合了
 *       {@code @TransactionalEventListener(phase = AFTER_COMMIT)}，
 *       保证事件在发布方事务<b>提交之后</b>才被处理。裸 {@code @EventListener}
 *       会在 {@code publishEvent} 调用点同步执行——此时事务尚未提交，
 *       订阅方可能读到未提交的数据，或者事务回滚后副作用已经发生。</li>
 *   <li><b>可靠性：</b>{@code @ApplicationModuleListener} 配合
 *       Event Publication Registry 持久化未完成的事件，监听器失败时
 *       可在应用重启或重试机制下重新投递。裸 {@code @EventListener}
 *       没有这个保障——监听器抛异常时事件直接丢失。</li>
 * </ul>
 * 对 {@link cn.realm.cloud.system.server.menu.events.MenuDeletedEvent} 而言，
 * 这一点尤其关键：删除事务回滚时如果订阅方已经清了 {@code iam_role_menu}，
 * 菜单还在但角色授权已经丢失，数据不一致且难以追溯。
 *
 * <p><b>幂等性要求：</b>由于 Event Publication Registry 的至少一次投递语义，
 * 订阅方必须保证处理幂等——按事件中的 {@code id} 清理关联、重复执行
 * 结果与执行一次相同。删除事件的幂等性由“删除不存在的记录影响 0 行”
 * 自然保证；其他事件如果涉及写操作，订阅方需自行保证。
 *
 * <p><b>字段命名约定：</b>本包下所有事件的主标识字段统一命名为 {@code id}，
 * 与 {@code user} / {@code role} / {@code organization} 模块的事件保持一致。
 * 避免出现“同一项目里四个模块用 {@code id}、一个模块用 {@code menuId}”
 * 的命名分裂。
 *
 * <p><b>事件是业务事实，与发起端无关：</b>发布时机在 {@code MenuServiceImpl}
 * 的事务内，但事件本身的语义是“菜单状态发生了某个变化”，而不是“管理后台
 * 发起了一个请求”。未来如果增加其他入口（批量操作、数据迁移、定时任务）
 * 触发同类变化，也应发布相同的事件，订阅方不需要感知变化的来源。
 *
 * @author QI Guang
 */
@NamedInterface("events")
package cn.realm.cloud.system.server.menu.events;

import org.springframework.modulith.NamedInterface;
