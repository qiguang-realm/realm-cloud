package cn.realm.cloud.system.server.menu.internal.persistence;

import cn.realm.cloud.system.server.menu.internal.persistence.mapper.MenuMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link MenuRepository} 的 MyBatis-Plus 实现。
 *
 * <p>MyBatis-Plus 的 {@link MenuMapper} 仅在本类内部使用，不向上泄漏到
 * {@code internal.application}。
 *
 * <p><b>逻辑删除注意：</b>{@code MenuDO} 未标注 {@code @TableLogic}，
 * MyBatis-Plus 不会自动追加 {@code is_deleted = 0} 条件。
 * <b>所有读写操作</b>（包括 {@link #findById}、{@link #findByIds}、
 * {@link #save} 的更新分支）都必须<b>显式</b>加 {@code eq(MenuDO::getIsDeleted, 0)}。
 * 遗漏会导致已删除记录被当作有效数据返回，或在写路径上被“改活”。
 *
 * <p><b>{@code null} 字段注意：</b>{@code updateById} 和
 * {@code update(entity, wrapper)} 默认忽略 {@code null} 字段。
 * 凡是需要把某列置空的场景（如逻辑删除时清空 {@code activePermissionCode}、
 * 更新时把 {@code permissionCode} 改为空），必须使用
 * {@link LambdaUpdateWrapper} 显式 {@code set}，不能依赖实体字段为 {@code null}。
 *
 * <p><b>职责边界：本类只做持久化读写，不做业务规则判断。</b>
 * {@code ancestors} 的拼接、{@code menuType} 的类型校验、删除前的子菜单校验，
 * 均由 {@code MenuServiceImpl} 在事务内编排。
 *
 * <p><b>与 {@code DepartmentRepositoryImpl} 的对称性：</b>两个类都是树形结构、
 * 都在 {@code save} 的更新分支不 set 结构性字段（{@code parentId} /
 * {@code ancestors}）、都在 {@code updateAncestorsBatch} 中逐条更新祖先链。
 * {@code MenuDO} 只有一个逻辑删除辅助列（{@code activePermissionCode}），
 * {@code DepartmentDO} 也只有一个（{@code activeDepartmentName}）——
 * 两者都只维护一个辅助列，比 {@code RoleDO} / {@code PositionDO}（各两个）简单。
 *
 * @author QI Guang
 */
@Repository
@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepository {

    /**
     * 未删除标记
     */
    private static final int NOT_DELETED = 0;

    /**
     * 已删除标记
     */
    private static final int DELETED = 1;

    private final MenuMapper menuMapper;

    // ==================== 查询 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>不使用 {@code selectById}——{@code MenuDO} 没有
     * {@code @TableLogic}，{@code selectById} 不会过滤逻辑删除，会返回
     * 已删除记录。改用 {@code selectOne} + 显式 {@code is_deleted = 0} 条件。
     */
    @Override
    public Optional<MenuDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(menuMapper.selectOne(
                Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getId, id)
                        .eq(MenuDO::getIsDeleted, NOT_DELETED)));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>权限标识在未删除菜单内唯一，由辅助列
     * {@code active_permission_code} 的唯一约束保证。本查询显式带
     * {@code is_deleted = 0}，避免查到已删除的菜单。
     */
    @Override
    public Optional<MenuDO> findByPermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(menuMapper.selectOne(
                Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getPermissionCode, permissionCode)
                        .eq(MenuDO::getIsDeleted, NOT_DELETED)));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>不使用 {@code selectBatchIds}——同 {@link #findById}，
     * 它不过滤逻辑删除。改用 {@code selectList} + {@code in} + 显式
     * {@code is_deleted = 0}。
     */
    @Override
    public List<MenuDO> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectList(
                Wrappers.<MenuDO>lambdaQuery()
                        .in(MenuDO::getId, ids)
                        .eq(MenuDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>排序在 SQL 层完成（{@code sort_order} 升序、{@code id} 升序），
     * {@code sort_order} 为 {@code null} 的记录会排在最前（MySQL 默认行为）。
     * 业务期望 {@code null} 排末尾，由 {@code MenuServiceImpl.listAll()}
     * 在 Service 层用 {@code Comparator.nullsLast} 重排修正。
     */
    @Override
    public List<MenuDO> findAll() {
        return menuMapper.selectList(
                Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getIsDeleted, NOT_DELETED)
                        .orderByAsc(MenuDO::getSortOrder)
                        .orderByAsc(MenuDO::getId));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>{@code menuType} 为 {@code null} 时通过
     * {@code .eq(condition, ...)} 的条件短路跳过类型过滤，等价于
     * {@link #findAll()}，但保留独立的 SQL 构建路径，便于未来在
     * {@code findByType} 上追加与 {@code findAll} 不同的过滤条件。
     */
    @Override
    public List<MenuDO> findByType(Integer menuType) {
        return menuMapper.selectList(
                Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getIsDeleted, NOT_DELETED)
                        .eq(menuType != null, MenuDO::getMenuType, menuType)
                        .orderByAsc(MenuDO::getSortOrder)
                        .orderByAsc(MenuDO::getId));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>排序在 SQL 层完成，供树组装时按层级顺序遍历。
     * 树组装本身由 {@code MenuService.listTree} 完成，本方法只保证
     * 同层节点有序。
     */
    @Override
    public List<MenuDO> findByParentId(Long parentId) {
        if (parentId == null) {
            return List.of();
        }
        return menuMapper.selectList(
                Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getIsDeleted, NOT_DELETED)
                        .eq(MenuDO::getParentId, parentId)
                        .orderByAsc(MenuDO::getSortOrder)
                        .orderByAsc(MenuDO::getId));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>使用 {@code likeRight} 生成
     * {@code ancestors LIKE 'prefix,%'}。<b>末尾补逗号是关键</b>：
     * {@code ancestors} 是逗号分隔的 ID 链，节点 5 的子孙形如
     * {@code "0,1,5,8"}，不补逗号时 {@code LIKE '0,1,5%'} 会误匹配
     * {@code "0,1,50"} 这类兄弟分支。补逗号后 {@code '0,1,5,%'} 只匹配
     * 以 {@code "0,1,5,"} 开头的链，同时自然排除了节点 5 自身
     * （其 {@code ancestors} 是 {@code "0,1"}，不以 {@code "0,1,5,"} 开头）。
     */
    @Override
    public List<MenuDO> findByAncestorsPrefix(String ancestorsPrefix) {
        if (ancestorsPrefix == null || ancestorsPrefix.isBlank()) {
            return List.of();
        }
        return menuMapper.selectList(
                Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getIsDeleted, NOT_DELETED)
                        .likeRight(MenuDO::getAncestors, ancestorsPrefix + ","));
    }

    // ==================== 存在性判断 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>使用 {@code menuMapper.exists} 生成
     * {@code SELECT 1 ... LIMIT 1} 形式的查询，比 {@code selectCount > 0}
     * 少一次全量计数。需要 MyBatis-Plus 3.5.3.2 及以上版本提供
     * {@code exists(Wrapper)} 方法。
     */
    @Override
    public boolean existsByPermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return menuMapper.exists(
                Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getPermissionCode, permissionCode)
                        .eq(MenuDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>{@code excludeId} 为 {@code null} 时委托给
     * {@link #existsByPermissionCode(String)}，避免在 wrapper 中拼接
     * 一个无意义的 {@code ne(id, null)} 条件。
     */
    @Override
    public boolean existsByPermissionCodeAndIdNot(String permissionCode, Long excludeId) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        if (excludeId == null) {
            return existsByPermissionCode(permissionCode);
        }
        return menuMapper.exists(
                Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getPermissionCode, permissionCode)
                        .ne(MenuDO::getId, excludeId)
                        .eq(MenuDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>显式带 {@code is_deleted = 0}。这一条是
     * {@code deleteMenu} 子菜单守卫的关键——如果过滤条件缺失，
     * 一个已逻辑删除的子菜单会永远阻止父菜单删除，且用户在前端
     * 看不到这个子菜单，无法排查。
     */
    @Override
    public boolean existsByParentId(Long parentId) {
        if (parentId == null) {
            return false;
        }
        return menuMapper.exists(
                Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getParentId, parentId)
                        .eq(MenuDO::getIsDeleted, NOT_DELETED));
    }

    // ==================== 写入 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>更新分支的处理策略：</b>不使用 {@code updateById}，而是用
     * {@link LambdaUpdateWrapper} 一次性完成「条件 + 辅助列 + 业务字段」：
     * <ul>
     *   <li>条件里显式带 {@code id} 和 {@code is_deleted = 0}，
     *       避免把已逻辑删除的记录改活</li>
     *   <li>{@code activePermissionCode} 用 {@code set} 显式赋值，
     *       允许为 {@code null}，不依赖 {@code updateById} 的非空字段策略</li>
     *   <li>业务字段逐项 {@code set}，避免两次 update 的中间态</li>
     * </ul>
     *
     * <p><b>不 set 结构性字段：</b>{@code menuType} / {@code parentId} /
     * {@code ancestors} 是树结构与类型的核心字段，变更会触发整棵子树的
     * 祖先链重算或类型规则重校验。它们由独立的用例处理
     * （move 操作走 {@link #updateAncestorsBatch(List)}，
     * 类型变更走独立的语义），不复用普通编辑。
     * {@code MenuServiceImpl.updateMenu} 已用数据库原值覆盖这三个字段，
     * Repository 层不 set 是第二重保护——即使调用方绕过 Service 直接
     * 传入不同的值，SQL 里也没有 {@code SET menu_type = ?} 等语句。
     *
     * <p><b>审计字段：</b>{@code updateTime} / {@code updateBy} 由调用方在
     * 传入的 {@code menu} 上设置，本方法原样写入。如果项目配置了
     * MyBatis-Plus 的 {@code MetaObjectHandler} 自动填充，注意
     * {@code update(null, wrapper)} 不会触发自动填充——此时需要保留
     * {@code menu} 上的审计字段值，或在 wrapper 中显式 {@code set}。
     */
    @Override
    public void save(MenuDO menu) {
        if (menu == null) {
            throw new IllegalArgumentException("menu 不能为空");
        }

        String permissionCode = menu.getPermissionCode();
        boolean hasPermissionCode = permissionCode != null && !permissionCode.isBlank();

        // ---------- 插入 ----------
        if (menu.getId() == null) {
            // 辅助列同步设置，保证唯一约束生效
            menu.setActivePermissionCode(hasPermissionCode ? permissionCode : null);
            menu.setIsDeleted(NOT_DELETED);
            menuMapper.insert(menu);
            return;
        }

        // ---------- 更新 ----------
        // 不 set menuType / parentId / ancestors：结构性字段，由独立的
        // move / 类型变更用例处理，Service 层已用数据库原值覆盖，
        // Repository 层不 set 是第二重保护。
        LambdaUpdateWrapper<MenuDO> wrapper = Wrappers.<MenuDO>lambdaUpdate()
                .eq(MenuDO::getId, menu.getId())
                .eq(MenuDO::getIsDeleted, NOT_DELETED)
                .set(MenuDO::getActivePermissionCode,
                        hasPermissionCode ? permissionCode : null)
                .set(MenuDO::getMenuName, menu.getMenuName())
                .set(MenuDO::getSortOrder, menu.getSortOrder())
                .set(MenuDO::getPath, menu.getPath())
                .set(MenuDO::getIcon, menu.getIcon())
                .set(MenuDO::getComponent, menu.getComponent())
                .set(MenuDO::getComponentName, menu.getComponentName())
                .set(MenuDO::getPermissionCode, menu.getPermissionCode())
                .set(MenuDO::getStatus, menu.getStatus())
                .set(MenuDO::getIsVisible, menu.getIsVisible())
                .set(MenuDO::getIsKeepAlive, menu.getIsKeepAlive())
                .set(MenuDO::getIsAlwaysShow, menu.getIsAlwaysShow())
                .set(MenuDO::getUpdateBy, menu.getUpdateBy())
                .set(MenuDO::getUpdateTime, menu.getUpdateTime());

        menuMapper.update(null, wrapper);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>逐条更新，每条只 {@code set} 一列 {@code ancestors}，
     * 不整行回写，避免覆盖并发修改的其他字段。
     *
     * <p><b>性能说明：</b>子孙量大时应改为 {@code CASE WHEN} 批量更新或
     * 分批 {@code IN} 更新。当前实现适合单次移动影响的子孙数量较小的场景
     * （菜单树通常深度有限、单节点子孙数不大）。
     */
    @Override
    public void updateAncestorsBatch(List<MenuDO> menus) {
        if (menus == null || menus.isEmpty()) {
            return;
        }
        for (MenuDO menu : menus) {
            if (menu.getId() == null) {
                continue;
            }
            menuMapper.update(null,
                    Wrappers.<MenuDO>lambdaUpdate()
                            .eq(MenuDO::getId, menu.getId())
                            .eq(MenuDO::getIsDeleted, NOT_DELETED)
                            .set(MenuDO::getAncestors, menu.getAncestors()));
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>必须用 {@link LambdaUpdateWrapper} 显式 {@code set}：
     * {@code updateById} 会忽略 {@code null}，导致
     * {@code active_permission_code} 不被清空、唯一约束不释放。
     * 条件里带 {@code is_deleted = 0}，重复删除同一 ID 时第二次
     * 更新 0 行，不会把已删除记录的辅助列又改一遍。
     */
    @Override
    public void deleteById(Long id) {
        if (id == null) {
            return;
        }
        menuMapper.update(null,
                Wrappers.<MenuDO>lambdaUpdate()
                        .eq(MenuDO::getId, id)
                        .eq(MenuDO::getIsDeleted, NOT_DELETED)
                        .set(MenuDO::getIsDeleted, DELETED)
                        .set(MenuDO::getActivePermissionCode, null));
    }
}
