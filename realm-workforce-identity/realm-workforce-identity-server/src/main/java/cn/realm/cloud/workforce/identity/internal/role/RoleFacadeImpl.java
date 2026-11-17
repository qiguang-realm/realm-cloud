package cn.realm.cloud.workforce.identity.internal.role;

import cn.realm.cloud.workforce.identity.api.facade.dto.RoleDto;
import cn.realm.cloud.workforce.identity.internal.role.application.RoleMenuService;
import cn.realm.cloud.workforce.identity.internal.role.application.RoleService;
import cn.realm.cloud.workforce.identity.internal.role.infrastructure.persistence.RoleDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link RoleFacade} 的默认实现。
 *
 * <p>位于 {@code internal.role} 包，由 Spring Modulith 的 {@code verify()} 保护，
 * 其他子域无法直接引用本类。本类只做「{@code RoleService} / {@code RoleMenuService}
 * 查询 + {@code RoleDO} 到 {@link RoleDto} 的映射」，不承载任何业务规则或状态变更。
 *
 * <p><b>职责边界：</b>
 * <ul>
 *     <li>只读：不包含 {@code save}、{@code delete}、{@code update} 等写操作。</li>
 *     <li>数据隔离：绝不将底层的 {@code RoleDO} 暴露给调用方，必须通过 {@code toDto} 转换。</li>
 * </ul>
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
        // 去重，避免调用方传入重复 ID 导致 size 比对出错
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

    // ==================== 私有映射 ====================

    /**
     * {@code RoleDO} → {@link RoleDto}。
     *
     * <p>剥离基础设施层的内部字段（如 {@code tenantId}、{@code version}、
     * {@code activeRoleCode} 等），仅保留跨模块需要的只读数据。
     *
     * @param role 持久化实体；为 {@code null} 时返回 {@code null}
     * @return 跨模块只读契约
     */
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
