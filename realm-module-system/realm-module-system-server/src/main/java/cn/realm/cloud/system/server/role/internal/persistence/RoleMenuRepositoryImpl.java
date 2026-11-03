package cn.realm.cloud.system.server.role.internal.persistence;

import cn.realm.cloud.system.server.role.internal.persistence.mapper.RoleMenuMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link RoleMenuRepository} 的 MyBatis-Plus 实现。
 *
 * <p>MyBatis-Plus 的 {@code RoleMenuMapper} 仅在本类内部使用，
 * 不向上泄漏到 {@code internal.application}。
 *
 * <p><b>关联表的特殊约定：</b>{@code RoleMenuDO} 没有 {@code isDeleted}
 * 字段，没有逻辑删除辅助列，没有 {@code updateTime} / {@code updateBy}。
 * 因此本类的所有查询<b>不加</b> {@code is_deleted = 0} 条件，
 * 所有删除都是物理删除——这与 {@code MenuRepositoryImpl} 的写法不同，
 * 不能直接照搬。
 *
 * <p><b>{@code null} 字段注意：</b>本类不使用 {@code updateById}，
 * 也不依赖 {@code null} 字段的非空策略——写入只有 {@code insert} 和
 * {@code delete}，没有 {@code update}。因此不需要 {@code LambdaUpdateWrapper}
 * 来显式 {@code set null}，这比聚合根仓储简单。
 *
 * <p><b>与 {@link RoleDepartmentRepositoryImpl} 的同构：</b>两个类的结构
 * 完全一致，只是把 {@code departmentId} 换成 {@code menuId}、把
 * {@code RoleDepartmentDO} 换成 {@code RoleMenuDO}。维护者在两者之间
 * 切换时不需要重新理解一套规则。
 *
 * @author QI Guang
 */
@Repository
@RequiredArgsConstructor
public class RoleMenuRepositoryImpl implements RoleMenuRepository {

    private final RoleMenuMapper roleMenuMapper;

    // ==================== 查询 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>先查出完整记录，再映射为 {@code menuId}。
     * 没有直接用 {@code selectObjs} 或自定义 SQL 投影——关联表数据量小
     * （每个角色的菜单授权通常几十条），完整查询的开销可忽略，
     * 代码也更直白。如果未来数据量增长，可以改为在 XML 中写
     * {@code SELECT menu_id FROM iam_role_menu WHERE role_id = #{roleId}}
     * 的投影查询。
     *
     * <p>排序按 {@code id} 升序，保证结果确定。
     */
    @Override
    public List<Long> findMenuIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return roleMenuMapper.selectList(
                        Wrappers.<RoleMenuDO>lambdaQuery()
                                .eq(RoleMenuDO::getRoleId, roleId)
                                .orderByAsc(RoleMenuDO::getId))
                .stream()
                .map(RoleMenuDO::getMenuId)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>与单角色版本的差异：
     * <ul>
     *   <li>查询条件用 {@code in} 而非 {@code eq}，一次查询完成</li>
     *   <li>不排序——集合无序，调用方按需自行处理</li>
     *   <li>用 {@code Collectors.toUnmodifiableSet()} 收集，天然去重
     *       （跨角色授权的同一菜单只出现一次），且返回不可变集合，
     *       防止调用方意外修改</li>
     * </ul>
     *
     * <p><b>为什么不用 {@code selectObjs}：</b>{@code selectObjs} 只返回
     * 第一列的值，虽然 {@code iam_role_menu} 的第一列是 {@code id}，
     * 但依赖列顺序的写法在表结构变更时容易出错。{@code selectList}
     * + {@code map(getMenuId)} 更明确，开销也相近（关联表数据量小）。
     *
     * <p><b>悬空 roleId 的处理：</b>不校验角色是否存在。{@code IN}
     * 查询遇到不存在的 {@code roleId} 时只是不匹配任何行，
     * 不会报错，也不会影响其他角色的结果。
     */
    @Override
    public Set<Long> findMenuIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return roleMenuMapper.selectList(
                        Wrappers.<RoleMenuDO>lambdaQuery()
                                .in(RoleMenuDO::getRoleId, roleIds))
                .stream()
                .map(RoleMenuDO::getMenuId)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ==================== 写入 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>先删除该角色的所有关联，再批量插入新记录。
     * 两个步骤在同一事务内（由 Service 层的 {@code @Transactional} 保证），
     * 任一步失败整体回滚。
     *
     * <p><b>为什么先删后插而不是增量更新：</b>全量替换的语义是“最终状态
     * 是这些菜单”，先删后插天然实现这一语义，且幂等——重复调用结果相同。
     * 增量更新（比较新旧列表、计算增删）代码更复杂，且在并发下需要
     * 额外的锁或版本控制。
     *
     * <p><b>插入的审计字段：</b>只设置 {@code roleId} 和 {@code menuId}。
     * {@code tenantId} / {@code createBy} / {@code createTime} 依赖框架的
     * 租户拦截器和 {@code MetaObjectHandler} 自动填充。如果项目未配置
     * 自动填充，需要在构造 {@code RoleMenuDO} 时显式设置这三个字段，
     * 否则插入的记录这些列为 {@code null}，会导致租户隔离失效、
     * 审计追溯困难。
     *
     * <p><b>批量插入方式：</b>使用 {@code BaseMapperX.insertBatch(Collection)}，
     * 它内部委托给 MyBatis-Plus 的 {@code Db.saveBatch}，按默认批次大小
     * （1000）分批插入。菜单授权数量有限时通常一批完成。
     */
    @Override
    public void replaceByRoleId(Long roleId, List<Long> menuIds) {
        if (roleId == null) {
            return;
        }

        // 1. 先删：清理该角色的所有现有关联
        deleteByRoleId(roleId);

        // 2. 空列表直接返回——已删除旧关联，等价于清空
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }

        // 3. 再插：批量插入新关联
        List<RoleMenuDO> records = menuIds.stream()
                .map(menuId -> new RoleMenuDO()
                        .setRoleId(roleId)
                        .setMenuId(menuId))
                .toList();
        roleMenuMapper.insertBatch(records);
    }

    // ==================== 删除 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>物理删除，不是逻辑删除——{@code RoleMenuDO}
     * 没有 {@code isDeleted} 字段。删除条件只有 {@code role_id}，
     * 影响该角色的所有菜单关联。
     */
    @Override
    public void deleteByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        roleMenuMapper.delete(
                Wrappers.<RoleMenuDO>lambdaQuery()
                        .eq(RoleMenuDO::getRoleId, roleId));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>物理删除，不是逻辑删除。删除条件只有
     * {@code menu_id}，影响所有引用该菜单的角色关联。
     */
    @Override
    public void deleteByMenuId(Long menuId) {
        if (menuId == null) {
            return;
        }
        roleMenuMapper.delete(
                Wrappers.<RoleMenuDO>lambdaQuery()
                        .eq(RoleMenuDO::getMenuId, menuId));
    }
}
