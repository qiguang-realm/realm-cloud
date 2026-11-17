package cn.realm.cloud.workforce.identity.internal.role.events;

/**
 * 角色状态变更事件。
 *
 * <p>在 {@code role} 子域内角色状态成功变更后发布。状态未实际变化时
 * <b>不发布</b>——订阅方不会收到“状态从 1 变为 1”这类无意义的通知。
 *
 * <p><b>作用域：</b>本事件是 {@code realm-workforce-identity-server} 模块
 * <b>内部</b>的具名接口（Named Interface），<b>不</b>对外部 Maven 模块暴露。
 * 同模块的其他子域（如 {@code user}、{@code permission}）通过
 * {@code "role :: events"} 依赖本包。
 *
 * <p><b>典型消费场景：</b>
 * <ul>
 *     <li>{@code user} 子域：当角色被停用时，清理相关用户的权限缓存。</li>
 *     <li>{@code permission} 子域：重新计算有效权限点。</li>
 * </ul>
 *
 * <p><b>时序敏感性：</b>订阅方必须使用 {@code @ApplicationModuleListener}，
 * 不要用裸 {@code @EventListener}——后者会在事务提交前同步执行，
 * 一旦事务回滚，会导致缓存被错误清理，但数据库状态并未改变。
 *
 * @param id     角色 ID
 * @param status 变更后的新状态：0-正常、1-停用
 * @author QI Guang
 */
public record RoleStatusChangedEvent(
        Long id,
        Integer status
) {
}
