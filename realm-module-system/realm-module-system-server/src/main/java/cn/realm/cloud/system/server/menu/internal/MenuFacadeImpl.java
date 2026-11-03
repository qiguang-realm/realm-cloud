package cn.realm.cloud.system.server.menu.internal;

import cn.realm.cloud.system.server.menu.MenuDto;
import cn.realm.cloud.system.server.menu.MenuFacade;
import cn.realm.cloud.system.server.menu.internal.application.MenuService;
import cn.realm.cloud.system.server.menu.internal.persistence.MenuDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link MenuFacade} 的默认实现。
 *
 * <p>位于 {@code internal} 包，由 Spring Modulith 的 {@code verify()} 保护，
 * 其他模块无法直接引用本类。本类只做「{@code MenuService} 查询 +
 * {@code MenuDO} 到 {@link MenuDto} 的映射」，不承载业务规则。
 *
 * <p><b>为什么用 {@code @Component} 而不是 {@code @Service}：</b>
 * Facade 不承载业务逻辑，只是"转发 + 映射"。{@code @Service} 暗示
 * "业务服务"，与 Facade 的定位不符。与 {@code UserFacadeImpl} /
 * {@code OrganizationFacadeImpl} / {@code RoleFacadeImpl} 保持一致。
 *
 * @author QI Guang
 */
@Component
@RequiredArgsConstructor
public class MenuFacadeImpl implements MenuFacade {

    /**
     * 菜单状态：正常。
     *
     * <p>权限点聚合只返回状态为正常的菜单的权限点——停用菜单的权限点
     * 不应出现在用户的权限集合中。这个常量放在 Facade 内部而不是
     * 引用 {@code MenuService} 的常量，是因为"什么状态的菜单贡献权限点"
     * 是 Facade 的契约语义，不是 Service 的实现细节。
     */
    private static final int STATUS_NORMAL = 0;

    private final MenuService menuService;

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
        return menuService.findById(id).isPresent();
    }

    /**
     * {@inheritDoc}
     *
     * <p>实现方式：先 {@code listByIds} 查出实际存在的菜单，再比较数量。
     * 如果入参有重复 ID，先去重再比较，避免因重复 ID 导致数量不匹配
     * 而误判为"有菜单不存在"。
     */
    @Override
    public boolean existsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        List<Long> distinctIds = ids.stream().distinct().toList();
        return menuService.listByIds(distinctIds).size() == distinctIds.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<MenuDto> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return menuService.findById(id).map(MenuFacadeImpl::toDto);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MenuDto> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return menuService.listByIds(ids).stream()
                .map(MenuFacadeImpl::toDto)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>复用 {@code MenuService.listByIds}——它已经
     * 显式过滤了逻辑删除。本方法在此基础上再过滤 {@code status} 和
     * {@code permissionCode} 非空，最后去重收集。
     *
     * <p><b>为什么不过滤 {@code menuType}：</b>目录（{@code menuType=1}）
     * 通常没有 {@code permissionCode}，会被 {@code permissionCode}
     * 非空的过滤条件自然排除。按钮（{@code menuType=3}）和部分菜单
     * （{@code menuType=2}）可能有权限点，都应保留。不需要显式按类型过滤。
     *
     * <p><b>为什么不在这里做缓存：</b>缓存应该按用户维度做（
     * {@code LoginUser} 含 {@code permissions}），而不是按菜单集合。
     * 同一个菜单集合被不同用户重复查询的概率低，按菜单集合缓存的
     * 命中率远低于按用户缓存。缓存的责任在
     * {@code SystemLoginUserProvider.loadUserById}。
     */
    @Override
    public Set<String> findPermissionCodesByMenuIds(Collection<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Set.of();
        }
        return menuService.listByIds(List.copyOf(menuIds)).stream()
                .filter(m -> Objects.equals(m.getStatus(), STATUS_NORMAL))
                .map(MenuDO::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * {@code MenuDO} → {@link MenuDto}。
     *
     * <p>只映射跨模块需要的字段，不包含 {@code ancestors}、
     * {@code activePermissionCode}、审计字段、逻辑删除标记。
     */
    private static MenuDto toDto(MenuDO menu) {
        if (menu == null) {
            return null;
        }
        return new MenuDto(
                menu.getId(),
                menu.getParentId(),
                menu.getMenuName(),
                menu.getMenuType(),
                menu.getSortOrder(),
                menu.getPath(),
                menu.getComponent(),
                menu.getComponentName(),
                menu.getIcon(),
                menu.getPermissionCode(),
                menu.getStatus(),
                menu.getIsVisible()
        );
    }
}
