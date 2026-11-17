package cn.realm.cloud.workforce.identity.internal.user;

import cn.realm.cloud.framework.core.security.dto.PasswordAuthenticationToken;
import cn.realm.cloud.workforce.identity.api.facade.dto.UserDto;
import cn.realm.cloud.workforce.identity.internal.auth.domain.model.UserIdentifierType;
import cn.realm.cloud.workforce.identity.internal.menu.MenuFacade;
import cn.realm.cloud.workforce.identity.internal.role.RoleFacade;
import cn.realm.cloud.workforce.identity.internal.user.application.UserService;
import cn.realm.cloud.workforce.identity.internal.user.domain.repository.UserRoleRepository;
import cn.realm.cloud.workforce.identity.internal.user.infrastructure.persistence.UserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link UserFacade} 的默认实现。
 *
 * <p>位于 {@code internal.user} 包，由 Spring Modulith 的 {@code verify()} 保护，
 * 其他子域（如 {@code auth}、{@code role}）通过注入 {@link UserFacade} 接口
 * 访问本类能力，<b>无法</b>直接引用本实现类。
 *
 * <p>本类做「{@code UserService} 查询 + {@code UserDO} 到 {@link UserDto} 的映射」，
 * 以及权限点聚合（{@code user → role → menu} 单向链路）。
 *
 * <p><b>没有身份绑定方法：</b>当前表结构不支持“一个账号绑多个第三方身份”。
 * 用户的登录标识就是 {@code iam_user} 上的三个字段（{@code username} /
 * {@code email} / {@code phone_number}），三者共用同一个密码哈希。
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
     * <p><b>实现说明：</b>接收 {@code String type} 以解耦子域依赖。
     * 内部将其转为枚举，如果传入非法字符串则返回 {@link Optional#empty()}。
     * 三种登录标识的查询完全平行，区别只在查询字段。
     */
    @Override
    public Optional<PasswordAuthenticationToken> findCredential(String type, String identifier) {
        if (type == null || identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        // 将字符串安全转换为业务枚举
        UserIdentifierType identifierType;
        try {
            identifierType = UserIdentifierType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return switch (identifierType) {
            case USERNAME -> userService.findByUsername(identifier)
                    .map(UserFacadeImpl::toPasswordAuthenticationToken);
            case EMAIL -> userService.findByEmail(identifier)
                    .map(UserFacadeImpl::toPasswordAuthenticationToken);
            case PHONE -> userService.findByPhoneNumber(identifier)
                    .map(UserFacadeImpl::toPasswordAuthenticationToken);
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>三步聚合，每步都做空集合短路。这是登录热路径，
     * 短路能省下相当一部分无效查询。
     *
     * <p><b>单次请求的三次查询：</b>
     * <ol>
     *   <li>{@code SELECT role_id FROM iam_user_role WHERE user_id = ?}（user 子域）</li>
     *   <li>{@code SELECT menu_id FROM iam_role_menu WHERE role_id IN (...)}（role 子域）</li>
     *   <li>{@code SELECT permission_code FROM iam_menu WHERE id IN (...)}（menu 子域）</li>
     * </ol>
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
     * 逻辑删除标记。
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
     * {@code UserDO} → {@link PasswordAuthenticationToken}。
     *
     * <p><b>只在登录流程中使用</b>——认证成功后立即丢弃，不缓存、不传递到
     * 其他模块。这是密码哈希不应该离开 {@code user} 子域的唯一例外。
     */
    private static PasswordAuthenticationToken toPasswordAuthenticationToken(UserDO user) {
        if (user == null) {
            return null;
        }
        return new PasswordAuthenticationToken(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getStatus(),
                user.getTenantId()
        );
    }
}
