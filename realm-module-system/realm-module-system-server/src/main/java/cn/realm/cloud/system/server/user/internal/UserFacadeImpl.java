package cn.realm.cloud.system.server.user.internal;

import cn.realm.cloud.framework.security.core.authenticator.LoginIdentifierType;
import cn.realm.cloud.system.server.menu.MenuFacade;
import cn.realm.cloud.system.server.role.RoleFacade;
import cn.realm.cloud.system.server.user.UserCredentialDto;
import cn.realm.cloud.system.server.user.UserDto;
import cn.realm.cloud.system.server.user.UserFacade;
import cn.realm.cloud.system.server.user.internal.application.UserService;
import cn.realm.cloud.system.server.user.internal.persistence.UserDO;
import cn.realm.cloud.system.server.user.internal.persistence.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link UserFacade} 的默认实现。
 *
 * <p>位于 {@code internal} 包，由 Spring Modulith 的 {@code verify()} 保护，
 * 其他模块无法直接引用本类。本类做「{@code UserService} 查询 +
 * {@code UserDO} 到 {@link UserDto} 的映射」，以及权限点聚合
 * （{@code user → role → menu} 单向链路）。
 *
 * <p>与 {@code OrganizationFacadeImpl} / {@code RoleFacadeImpl} /
 * {@code MenuFacadeImpl} / {@code PermissionFacadeImpl} 结构一致：
 * {@code @Component} + 私有静态 {@code toDto} 方法。
 *
 * <p><b>没有身份绑定方法：</b>当前表结构不支持“一个账号绑多个第三方身份”。
 * 用户的登录标识就是 {@code iam_user} 上的三个字段（{@code username} /
 * {@code email} / {@code phone_number}），三者共用同一个密码哈希。
 * 第三方登录 / Passkey / TOTP 必须新增表才能支持。
 *
 * @author QI Guang
 */
@Component
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {

    private final UserService userService;
    private final UserRoleRepository userRoleRepository;
    private final RoleFacade roleFacade;
    private final MenuFacade menuFacade;

    // ==================== 查询 ====================

    @Override
    public boolean existsById(Long id) {
        if (id == null) {
            return false;
        }
        return userService.findById(id).isPresent();
    }

    @Override
    public boolean existsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        List<Long> distinctIds = ids.stream().distinct().toList();
        return userService.listByIds(distinctIds).size() == distinctIds.size();
    }

    @Override
    public Optional<UserDto> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return userService.findById(id).map(UserFacadeImpl::toDto);
    }

    @Override
    public List<UserDto> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userService.listByIds(ids).stream()
                .map(UserFacadeImpl::toDto)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>三种登录标识的查询完全平行——都走
     * {@code UserService} 的对应方法，区别只在查询字段。{@code switch}
     * 在这里是合适的，因为分支数量固定为 3，且每个分支只有一行调用。
     *
     * <p><b>为什么不用策略模式 / 注册表：</b>三种标识的查询逻辑完全一致
     * （都是“按某个唯一字段查未删除用户”），引入策略接口只会增加一个
     * 无意义的三实现类文件。如果未来标识类型超过 5 个，或不同标识的
     * 查询逻辑出现差异（如手机号需要解密、邮箱需要大小写归一化），
     * 再考虑抽策略。
     */
    @Override
    public Optional<UserCredentialDto> findCredential(LoginIdentifierType type, String identifier) {
        if (type == null || identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        return switch (type) {
            case USERNAME -> userService.findByUsername(identifier)
                    .map(UserFacadeImpl::toCredential);
            case EMAIL -> userService.findByEmail(identifier)
                    .map(UserFacadeImpl::toCredential);
            case PHONE -> userService.findByPhoneNumber(identifier)
                    .map(UserFacadeImpl::toCredential);
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>三步聚合，每步都做空集合短路——第一步无角色时
     * 直接返回空，不浪费后续两次查询。这是登录热路径，短路能省下
     * 相当一部分无效查询（尤其是“用户从未分配角色”的场景）。
     *
     * <p><b>单次请求的三次查询：</b>
     * <ol>
     *   <li>{@code SELECT role_id FROM iam_user_role WHERE user_id = ?}（user 模块）</li>
     *   <li>{@code SELECT menu_id FROM iam_role_menu WHERE role_id IN (...)}（role 模块）</li>
     *   <li>{@code SELECT permission_code FROM iam_menu WHERE id IN (...)}（menu 模块）</li>
     * </ol>
     * 缓存建议见接口 Javadoc。
     */
    @Override
    public Set<String> findPermissionsByUserId(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        // 1. 用户 → 角色 ID
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        // 2. 角色 → 菜单 ID
        Set<Long> menuIds = roleFacade.findMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return Set.of();
        }
        // 3. 菜单 → 权限点编码
        return menuFacade.findPermissionCodesByMenuIds(menuIds);
    }

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
    @Override
    public List<Long> findRoleIdsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userRoleRepository.findRoleIdsByUserId(userId);
    }

    // ==================== 私有映射 ====================

    /**
     * {@code UserDO} → {@link UserDto}。
     *
     * <p>只映射跨模块需要的字段，不包含 {@code passwordHash}、审计字段、
     * 逻辑删除标记。{@code passwordHash} 只通过 {@link UserCredentialDto}
     * 在登录流程中传递，不进入通用用户信息契约。
     */
    private static UserDto toDto(UserDO user) {
        if (user == null) {
            return null;
        }
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getDepartmentId(),
                user.getStatus(),
                user.getTenantId()
        );
    }

    /**
     * {@code UserDO} → {@link UserCredentialDto}。
     *
     * <p><b>只在登录流程中使用</b>——认证成功后立即丢弃，不缓存、不传递到
     * 其他模块。这是密码哈希不应该离开 {@code user} 模块的唯一例外。
     */
    private static UserCredentialDto toCredential(UserDO user) {
        if (user == null) {
            return null;
        }
        return new UserCredentialDto(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getStatus(),
                user.getTenantId()
        );
    }
}
