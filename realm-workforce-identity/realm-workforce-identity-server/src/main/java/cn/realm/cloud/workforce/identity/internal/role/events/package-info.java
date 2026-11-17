/**
 * 角色子域的事件发布包。
 *
 * <p>通过 {@code @NamedInterface("events")} 声明为具名接口。
 * 同模块的其他子域（如 {@code user}）可以依赖：{@code "role :: events"}。
 * 外部 Maven 模块无法访问此包。
 */
@org.springframework.modulith.NamedInterface("events")
package cn.realm.cloud.workforce.identity.internal.role.events;
