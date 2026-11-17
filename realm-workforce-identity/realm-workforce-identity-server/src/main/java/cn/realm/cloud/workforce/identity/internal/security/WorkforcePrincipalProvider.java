package cn.realm.cloud.workforce.identity.internal.security;

import cn.realm.cloud.framework.core.principal.UserPrincipal;
import cn.realm.cloud.framework.core.security.dto.PasswordAuthenticationToken;
import cn.realm.cloud.framework.core.security.provider.PrincipalProvider;
import cn.realm.cloud.workforce.identity.internal.auth.domain.model.UserIdentifierType;
import cn.realm.cloud.workforce.identity.internal.user.UserFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Workforce IAM 的安全主体提供者实现。
 *
 * <p><b>职责：</b>实现框架层的 {@link PrincipalProvider} SPI，为认证流程提供
 * 数据库中的用户数据与权限点。
 *
 * <p><b>性能提醒：</b>{@link #loadUserById(Long)} 在每次请求的 Token 校验阶段都会被调用，
 * 内部触发了用户查询和权限查询。生产环境<b>必须</b>在 Facade 或此实现类外部增加缓存
 * （如 Caffeine 或 Redis），缓存 key 为 {@code userId}，失效时机应与角色、菜单、
 * 用户状态的变更事件联动。
 *
 * @author QI Guang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkforcePrincipalProvider implements PrincipalProvider {

    private final UserFacade userFacade;

    @Override
    public Optional<PasswordAuthenticationToken> loadCredential(String identifierType, String identifier) {
        // 1. 参数防御：直接拦截空值，避免后续 toUpperCase 引发 NPE
        if (identifierType == null || identifierType.isBlank()) {
            return Optional.empty();
        }

        // 2. 安全地将字符串转换为业务枚举
        UserIdentifierType type;
        try {
            type = UserIdentifierType.valueOf(identifierType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知的登录标识类型: {}", identifierType);
            return Optional.empty();
        }

        // 3. 调用 Facade 查询并映射为框架层的 PasswordAuthenticationToken
        // 注意：UserFacade.findCredential 签名接收 String 类型的 type
        return userFacade.findCredential(type.name(), identifier)
                // 防御：已停用用户(status=1)直接拦截，不进行密码比对，节省 CPU 消耗
                .filter(dto -> dto.status() != null && dto.status() == 0)
                .map(dto -> new PasswordAuthenticationToken(
                        dto.id(),
                        dto.username(),
                        dto.passwordHash(),
                        dto.status(),
                        dto.tenantId()
                ));
    }

    @Override
    public Optional<UserPrincipal> loadUserById(Long userId) {
        return userFacade.findById(userId)
                // 防御：已停用用户不再加载主体
                .filter(dto -> dto.status() != null && dto.status() == 0)
                .map(dto -> {
                    // 聚合权限点（耗时操作，务必加缓存）
                    Set<String> permissions = userFacade.findPermissionsByUserId(userId);

                    return new UserPrincipal(
                            dto.id(),
                            dto.username(),
                            dto.nickname(),
                            dto.status(),
                            dto.tenantId(),
                            permissions
                    );
                });
    }
}
