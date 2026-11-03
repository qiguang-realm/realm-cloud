/**
 * 菜单模块。
 *
 * <p>菜单是 RBAC 中被授权的资源定义，以 {@code parentId} + {@code ancestors} 组织为树形结构。
 * 类型 {@code menuType}：1-目录、2-菜单、3-按钮。模块拥有菜单的完整生命周期
 * （创建、更新、移动、启停、逻辑删除），并对外暴露只读查询供授权模块使用。
 *
 * <p><b>公共 API：</b>
 * <ul>
 *   <li>根包下的 {@link cn.realm.cloud.system.server.menu.MenuFacade}：
 *       供 {@code role} 在分配菜单前校验 {@code menuId} 有效性（{@code existsByIds}），
 *       以及供运行时权限校验读取权限标识。只暴露只读方法，写操作不通过 Facade。</li>
 *   <li>根包下的 {@link cn.realm.cloud.system.server.menu.MenuDto}：
 *       跨模块只读契约，不携带持久化注解。</li>
 *   <li>{@code menu::events} 命名接口（见 {@code events/package-info.java}）：
 *       对外发布 {@code MenuDeletedEvent}、{@code MenuStatusChangedEvent}。</li>
 * </ul>
 *
 * <p><b>依赖边界（关键）：</b>
 * 本模块只依赖 {@code shared}，<b>不依赖</b> {@code role}、{@code user} 等任何业务模块。
 * 菜单被角色引用的情况由 {@code role} 模块监听 {@code MenuDeletedEvent} 异步清理，
 * 而不是让 {@code menu} 反向同步校验——后者会与 {@code role → menu::events}
 * 构成循环依赖。若未来需要在删除菜单时同步拒绝“有角色引用”的操作，
 * 应把编排入口移到 {@code role} 模块，而不是在本模块新增对 {@code role} 的依赖。
 *
 * <p>内部实现位于 {@code internal} 子包（{@code api} / {@code application} /
 * {@code persistence}），对其他模块不可见，由 {@code ApplicationModules.verify()} 强制。
 *
 * @author QI Guang
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared"}
)
package cn.realm.cloud.system.server.menu;
