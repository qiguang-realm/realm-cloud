package cn.realm.cloud.system.server.user.internal.application;

import cn.realm.cloud.system.server.role.RoleFacade;
import cn.realm.cloud.system.server.user.internal.persistence.UserRepository;
import cn.realm.cloud.system.server.user.internal.persistence.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * {@link UserRoleService} 的默认实现。
 *
 * <p>依赖三个协作方：
 * <ul>
 *   <li>{@link UserRoleRepository}：关联表自身的读写</li>
 *   <li>{@link UserRepository}：确认用户存在，避免为不存在的用户写关联</li>
 *   <li>{@link RoleFacade}：校验角色 ID 的合法性，不接触 {@code role} 的 {@code internal}</li>
 * </ul>
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final RoleFacade roleFacade;

    @Override
    public List<Long> findRoleIdsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userRoleRepository.findRoleIdsByUserId(userId);
    }

    @Override
    @Transactional
    public void replaceUserRoles(Long userId, List<Long> roleIds) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        if (roleIds != null && !roleIds.isEmpty() && !roleFacade.existsByIds(roleIds)) {
            throw new IllegalArgumentException("存在无效的角色 ID");
        }
        userRoleRepository.replaceUserRoles(userId, roleIds);
        log.info("替换用户角色分配成功, userId={}, count={}",
                userId, roleIds == null ? 0 : roleIds.size());
    }
}
