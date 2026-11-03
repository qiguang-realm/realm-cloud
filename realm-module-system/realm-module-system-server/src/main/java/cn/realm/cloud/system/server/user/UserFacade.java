package cn.realm.cloud.system.server.user;

import cn.realm.cloud.framework.security.core.authenticator.LoginIdentifierType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 用户模块的跨模块门面。
 *
 * <p>本接口是 {@code user} 模块对外的<b>只读</b>能力边界。其他模块通过
 * 注入本接口获取用户信息，<b>不</b>直接引用 {@code user.internal} 中的
 * {@code UserService}、{@code UserRepository} 或 {@code UserDO}。
 * Spring Modulith 的 {@code verify()} 会阻止跨模块引用 {@code internal} 类型。
 *
 * <p><b>不支持身份绑定：</b>“一个账号绑多个第三方身份”需要一张独立的
 * 身份-账号关系表（{@code iam_user_identity} 或类似）。当前表结构下，
 * 用户的登录标识就是 {@code iam_user} 上的 {@code username} / {@code email}
 * / {@code phone_number} 三个字段，修改它们等同于修改用户资料
 * （走 {@code UserService.updateUser}），不存在“绑定/解绑”的语义。
 * 第三方登录 / Passkey / TOTP 必须新增表才能支持，不在当前范围内。
 *
 * <p><b>三种登录标识共用同一密码：</b>用户可以用用户名、邮箱、手机号
 * 任意一个 + 密码登录，三者共用同一个 {@code iam_user.password_hash}。
 * 修改密码后三种登录方式同步生效，不需要分别维护。
 *
 * @author QI Guang
 */
public interface UserFacade {

    // ==================== 查询 ====================

    /**
     * 判断用户是否存在（未删除）。
     *
     * @param id 用户 ID；为 {@code null} 时返回 {@code false}
     * @return 存在未删除的用户时为 {@code true}
     */
    boolean existsById(Long id);

    /**
     * 批量判断用户是否全部存在（未删除）。
     *
     * @param ids 用户 ID 列表；为 {@code null} 或空集合时返回 {@code true}
     * @return 全部存在时为 {@code true}，任一不存在时为 {@code false}
     */
    boolean existsByIds(List<Long> ids);

    /**
     * 按 ID 查询用户只读信息。
     *
     * @param id 用户 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除用户的只读信息；不存在或已逻辑删除时返回空
     */
    Optional<UserDto> findById(Long id);

    /**
     * 按主键批量查询用户只读信息。
     *
     * @param ids 用户 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除用户的只读信息列表，顺序不保证与入参一致
     */
    List<UserDto> findByIds(List<Long> ids);

    /**
     * 按登录标识查询登录凭据（含密码哈希）。
     *
     * <p>供 {@code LoginUserProvider.loadCredential} 使用——这是密码认证器
     * 的入口，只在登录时调用一次。
     *
     * <p>实现根据 {@code type} 选择查询字段：
     * <ul>
     *   <li>{@link LoginIdentifierType#USERNAME} → {@code iam_user.username}</li>
     *   <li>{@link LoginIdentifierType#EMAIL} → {@code iam_user.email}</li>
     *   <li>{@link LoginIdentifierType#PHONE} → {@code iam_user.phone_number}</li>
     * </ul>
     *
     * <p><b>空值处理：</b>{@code email} / {@code phone_number} 在
     * {@code iam_user} 上允许为 {@code null}——用户未填写邮箱时，
     * {@code findCredential(EMAIL, ...)} 永远返回空，该用户不能用邮箱登录。
     * 这是合理的行为，不是错误。
     *
     * @param type       登录标识类型；为 {@code null} 时返回 {@link Optional#empty()}
     * @param identifier 登录标识值；为 {@code null} 或空白时返回 {@link Optional#empty()}
     * @return 登录凭据；用户不存在或对应字段为空时返回空
     */
    Optional<UserCredentialDto> findCredential(LoginIdentifierType type, String identifier);

    /**
     * 查询用户的全部权限点编码。
     *
     * <p><b>聚合链路：</b>{@code iam_user_role}（本模块）→ {@code iam_role_menu}
     * （{@code RoleFacade.findMenuIdsByRoleIds}）→ {@code iam_menu.permission_code}
     * （{@code MenuFacade.findPermissionCodesByMenuIds}）。
     *
     * <p><b>为什么放在 {@code user} 而非 {@code permission}：</b>
     * {@code user} 已经依赖 {@code role} 和 {@code menu}，加这个聚合方法
     * 不引入新依赖；放在 {@code permission} 会形成 {@code user ↔ permission}
     * 循环。
     *
     * <p><b>性能说明：</b>每次调用走三次数据库查询。生产环境建议在
     * {@code SystemLoginUserProvider} 中加缓存（Caffeine / Redis），
     * 缓存 key 为 {@code userId}，失效时机与 {@code RoleDeletedEvent} /
     * {@code MenuDeletedEvent} / {@code UserStatusChangedEvent} 联动。
     *
     * @param userId 用户 ID；为 {@code null} 时返回空集合
     * @return 权限点编码集合；用户无角色或无授权时返回空集合
     */
    Set<String> findPermissionsByUserId(Long userId);

    /**
     * 查询用户已分配的角色 ID 列表。
     *
     * <p>{@code iam_user_role} 归 {@code user} 模块所有，所以“用户的角色”
     * 由 {@code user} 模块查询。供 {@code ProfileController} 组装菜单树
     * 使用：user → roleIds → menuIds → menuDtos。
     *
     * @param userId 用户 ID；为 {@code null} 时返回空列表
     * @return 角色 ID 列表；无角色时返回空列表
     */
    List<Long> findRoleIdsByUserId(Long userId);
}
