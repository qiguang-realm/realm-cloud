package cn.realm.cloud.system.server.role.internal;

import cn.realm.cloud.system.server.role.RoleDto;
import cn.realm.cloud.system.server.role.RoleFacade;
import cn.realm.cloud.system.server.role.internal.application.RoleMenuService;
import cn.realm.cloud.system.server.role.internal.application.RoleService;
import cn.realm.cloud.system.server.role.internal.persistence.RoleDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link RoleFacade} 的默认实现。
 *
 * <p>位于 {@code internal} 包，由 Spring Modulith 的 {@code verify()} 保护，
 * 其他模块无法直接引用本类。本类只做「{@code RoleService} / {@code RoleMenuService}
 * 查询 + {@code RoleDO} 到 {@link RoleDto} 的映射」，不承载业务规则。
 *
 * <p>与 {@code UserFacadeImpl} / {@code OrganizationFacadeImpl} /
 * {@code MenuFacadeImpl} / {@code PermissionFacadeImpl} 结构一致：
 * {@code @Component} + 私有静态 {@code toDto} 方法。
 *
 * @author QI Guang
 */
@Component
@RequiredArgsConstructor
public class RoleFacadeImpl implements RoleFacade {

    private final RoleService roleService;
    private final RoleMenuService roleMenuService;

    @Override
    public boolean existsById(Long id) {
        if (id == null) {
            return false;
        }
        return roleService.findById(id).isPresent();
    }

    @Override
    public boolean existsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        List<Long> distinctIds = ids.stream().distinct().toList();
        return roleService.listByIds(distinctIds).size() == distinctIds.size();
    }

    @Override
    public Optional<RoleDto> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return roleService.findById(id).map(RoleFacadeImpl::toDto);
    }

    @Override
    public List<RoleDto> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return roleService.listByIds(ids).stream()
                .map(RoleFacadeImpl::toDto)
                .toList();
    }

    @Override
    public Set<Long> findMenuIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return roleMenuService.findMenuIdsByRoleIds(roleIds);
    }

    private static RoleDto toDto(RoleDO role) {
        if (role == null) {
            return null;
        }
        return new RoleDto(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getStatus()
        );
    }
}
