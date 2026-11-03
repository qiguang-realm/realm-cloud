package cn.realm.cloud.system.server.security;

import cn.realm.cloud.framework.security.core.LoginUser;
import cn.realm.cloud.framework.security.core.LoginUserProvider;
import cn.realm.cloud.framework.security.core.authenticator.LoginIdentifierType;
import cn.realm.cloud.system.server.user.UserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 系统模块的登录用户提供者。
 *
 * <p>实现 starter 的 SPI，为认证流程提供用户数据。
 *
 * <p><b>权限点聚合由 {@code UserFacade.findPermissionsByUserId} 完成：</b>
 * 用户维度的权限点查询需要跨 {@code user} / {@code role} / {@code menu}
 * 三个模块的数据，由 {@code user} 模块聚合不会引入新依赖。
 *
 * <p><b>性能提醒：</b>本方法在 {@code TokenAuthenticationFilter} 中每个请求
 * 调用一次，内部触发 3 次数据库查询。生产环境<b>必须</b>在 {@code LoginUser}
 * 层加缓存（Caffeine 或 Redis），缓存 key 为 {@code userId}，
 * 失效时机与 {@code RoleDeletedEvent} / {@code MenuDeletedEvent} /
 * {@code UserStatusChangedEvent} 联动。
 *
 * @author QI Guang
 */
@Component
@RequiredArgsConstructor
public class SystemLoginUserProvider implements LoginUserProvider {

    private final UserFacade userFacade;

    @Override
    public Optional<LoginCredential> loadCredential(LoginIdentifierType type, String identifier) {
        return userFacade.findCredential(type, identifier)
                .map(dto -> new LoginCredential(
                        dto.id(), dto.username(), dto.passwordHash(),
                        dto.status(), dto.tenantId()));
    }

    @Override
    public Optional<LoginUser> loadUserById(Long userId) {
        return userFacade.findById(userId)
                .map(dto -> new LoginUser(
                        dto.id(), dto.username(), dto.nickname(),
                        dto.status(), dto.tenantId(),
                        userFacade.findPermissionsByUserId(userId)));
    }
}
