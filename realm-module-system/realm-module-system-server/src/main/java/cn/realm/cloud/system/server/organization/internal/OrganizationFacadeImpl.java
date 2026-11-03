package cn.realm.cloud.system.server.organization.internal;

import cn.realm.cloud.system.server.organization.DepartmentDto;
import cn.realm.cloud.system.server.organization.OrganizationFacade;
import cn.realm.cloud.system.server.organization.internal.application.DepartmentService;
import cn.realm.cloud.system.server.organization.internal.persistence.DepartmentDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link OrganizationFacade} 的默认实现。
 *
 * <p>位于 {@code internal} 包，由 Spring Modulith 的 {@code verify()} 保护，
 * 其他模块无法直接引用本类。本类只做「{@code DepartmentService} 查询 +
 * {@code DepartmentDO} 到 {@link DepartmentDto} 的映射」，不承载业务规则
 * ——业务规则留在 {@code DepartmentService} 中。
 *
 * <p><b>与 {@code DepartmentService} 的区别：</b>{@code DepartmentService}
 * 是模块内部的用例编排器，返回 {@code DepartmentDO}，供
 * {@code DepartmentAdminController} 和本类使用；{@code OrganizationFacade}
 * 是跨模块的只读门面，返回 {@link DepartmentDto}，只暴露其他模块
 * 真正需要的字段。
 *
 * @author QI Guang
 */
@Component
@RequiredArgsConstructor
public class OrganizationFacadeImpl implements OrganizationFacade {

    private final DepartmentService departmentService;

    /**
     * {@inheritDoc}
     *
     * <p>{@code id} 为 {@code null} 时直接返回 {@code false}，
     * 避免把 {@code null} 传给 {@code findById} 触发无意义的查询。
     */
    @Override
    public boolean existsById(Long id) {
        if (id == null) {
            return false;
        }
        return departmentService.findById(id).isPresent();
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现方式：先 {@code findByIds} 查出实际存在的部门，再比较数量。
     * 如果入参有重复 ID，先去重再比较，避免因重复 ID 导致数量不匹配
     * 而误判为“有部门不存在”。
     *
     * <p>空集合返回 {@code true}——语义上“无需校验”视为通过。
     */
    @Override
    public boolean existsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        List<Long> distinctIds = ids.stream().distinct().toList();
        return departmentService.listByIds(distinctIds).size() == distinctIds.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<DepartmentDto> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return departmentService.findById(id).map(OrganizationFacadeImpl::toDto);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DepartmentDto> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return departmentService.listByIds(ids).stream()
                .map(OrganizationFacadeImpl::toDto)
                .toList();
    }

    /**
     * {@code DepartmentDO} → {@link DepartmentDto}。
     *
     * <p>只映射跨模块需要的字段，不包含 {@code ancestors}、审计字段、
     * 逻辑删除标记、{@code activeDepartmentName}。与
     * {@code DepartmentAdminConverter.toResponse} 的映射规则类似，但后者
     * 位于 {@code internal.api.web.admin.converter} 包、服务于 HTTP 适配器；
     * 本方法是 Facade 层的私有映射，不依赖 {@code api} 层，避免
     * Facade 与 Controller 的 DTO 耦合。
     */
    private static DepartmentDto toDto(DepartmentDO department) {
        if (department == null) {
            return null;
        }
        return new DepartmentDto(
                department.getId(),
                department.getDepartmentName(),
                department.getParentId(),
                department.getStatus()
        );
    }
}
