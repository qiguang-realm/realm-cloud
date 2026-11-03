package cn.realm.cloud.system.server.organization.internal.persistence;

import cn.realm.cloud.system.server.organization.internal.persistence.mapper.DepartmentMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link DepartmentRepository} 的 MyBatis-Plus 实现。
 *
 * <p>MyBatis-Plus 的 {@link DepartmentMapper} 仅在本类内部使用，不向上泄漏到
 * {@code internal.application}。
 *
 * <p><b>逻辑删除注意：</b>{@code DepartmentDO} 未标注 {@code @TableLogic}，
 * MyBatis-Plus 不会自动追加 {@code is_deleted = 0} 条件。
 * <b>所有读写操作</b>（包括 {@link #findById}、{@link #findByIds}、
 * {@link #save} 的更新分支）都必须<b>显式</b>加 {@code eq(DepartmentDO::getIsDeleted, 0)}。
 * 遗漏会导致已删除记录被当作有效数据返回，或在写路径上被“改活”。
 *
 * <p><b>{@code null} 字段注意：</b>{@code updateById} 默认忽略 {@code null}
 * 字段。凡是需要把某列置空的场景（如逻辑删除时清空 {@code activeDepartmentName}、
 * 更新时把 {@code departmentName} 改为空），必须使用
 * {@link LambdaUpdateWrapper} 显式 {@code set}，不能依赖实体字段为 {@code null}。
 *
 * <p><b>部门名称唯一性的两种口径：</b>本类同时实现同级唯一（与 DDL 对齐，
 * 推荐）和全局唯一（保留供特定场景）两组校验方法。详细说明见
 * {@link DepartmentRepository} 的接口注释。
 *
 * @author QI Guang
 */
@Repository
@RequiredArgsConstructor
public class DepartmentRepositoryImpl implements DepartmentRepository {

    /**
     * 未删除标记
     */
    private static final int NOT_DELETED = 0;

    /**
     * 已删除标记
     */
    private static final int DELETED = 1;

    private final DepartmentMapper departmentMapper;

    // ==================== 查询 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>不使用 {@code selectById}——{@code DepartmentDO} 没有
     * {@code @TableLogic}，{@code selectById} 不会过滤逻辑删除，会返回
     * 已删除记录。改用 {@code selectOne} + 显式 {@code is_deleted = 0} 条件。
     */
    @Override
    public Optional<DepartmentDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(departmentMapper.selectOne(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .eq(DepartmentDO::getId, id)
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED)));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>不使用 {@code selectBatchIds}——同 {@link #findById}，
     * 它不过滤逻辑删除。改用 {@code selectList} + {@code in} + 显式
     * {@code is_deleted = 0}。
     */
    @Override
    public List<DepartmentDO> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return departmentMapper.selectList(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .in(DepartmentDO::getId, ids)
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>排序在 SQL 层完成（{@code sort_order} 升序、{@code id} 升序），
     * {@code sort_order} 为 {@code null} 的记录会排在最前（MySQL 默认行为）。
     * 业务期望 {@code null} 排末尾，由 {@code DepartmentServiceImpl.listAll()}
     * 在 Service 层用 {@code Comparator.nullsLast} 重排修正——
     * 本方法只保证“有确定顺序”，不修正 {@code NULL} 的位置。
     */
    @Override
    public List<DepartmentDO> findAll() {
        return departmentMapper.selectList(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED)
                        .orderByAsc(DepartmentDO::getSortOrder)
                        .orderByAsc(DepartmentDO::getId));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>排序在 SQL 层完成，供树组装时按层级顺序遍历。
     * 树组装本身由 {@code DepartmentService.listTree} 完成，本方法只保证
     * 同层节点有序。
     */
    @Override
    public List<DepartmentDO> findByParentId(Long parentId) {
        if (parentId == null) {
            return List.of();
        }
        return departmentMapper.selectList(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED)
                        .eq(DepartmentDO::getParentId, parentId)
                        .orderByAsc(DepartmentDO::getSortOrder)
                        .orderByAsc(DepartmentDO::getId));
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
    public List<DepartmentDO> findByAncestorsPrefix(String ancestorsPrefix) {
        if (ancestorsPrefix == null || ancestorsPrefix.isBlank()) {
            return List.of();
        }
        return departmentMapper.selectList(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED)
                        .likeRight(DepartmentDO::getAncestors, ancestorsPrefix + ","));
    }

    // ==================== 存在性判断：同级唯一（推荐） ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>查询条件与 DDL 的唯一索引
     * {@code uk_iam_department_tenant_parent_name}
     * {@code (tenant_id, parent_id, active_department_name)} 对应——
     * 按 {@code parent_id} + {@code department_name} + {@code is_deleted = 0} 校验。
     * 不同父部门下可以有同名部门，本方法不会误拒这类请求。
     *
     * <p>使用 {@code exists} 生成 {@code SELECT 1 ... LIMIT 1}，
     * 比 {@code selectCount > 0} 少一次全量计数。需要 MyBatis-Plus
     * 3.5.3.2 及以上版本提供 {@code exists(Wrapper)}。
     */
    @Override
    public boolean existsByParentIdAndDepartmentName(Long parentId, String departmentName) {
        if (parentId == null || departmentName == null || departmentName.isBlank()) {
            return false;
        }
        return departmentMapper.exists(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .eq(DepartmentDO::getParentId, parentId)
                        .eq(DepartmentDO::getDepartmentName, departmentName)
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>{@code excludeId} 为 {@code null} 时委托给
     * {@link #existsByParentIdAndDepartmentName(Long, String)}，
     * 避免在 wrapper 中拼接一个无意义的 {@code ne(id, null)} 条件——
     * {@code ne} 对 {@code null} 的处理依赖 MP 版本，不保险。
     */
    @Override
    public boolean existsByParentIdAndDepartmentNameAndIdNot(
            Long parentId, String departmentName, Long excludeId) {
        if (parentId == null || departmentName == null || departmentName.isBlank()) {
            return false;
        }
        if (excludeId == null) {
            return existsByParentIdAndDepartmentName(parentId, departmentName);
        }
        return departmentMapper.exists(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .eq(DepartmentDO::getParentId, parentId)
                        .eq(DepartmentDO::getDepartmentName, departmentName)
                        .ne(DepartmentDO::getId, excludeId)
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED));
    }

    // ==================== 存在性判断：全局唯一（保留，特定场景） ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>不区分 {@code parentId}，按 {@code department_name}
     * + {@code is_deleted = 0} 校验。比 DDL 的唯一约束更严格，
     * 会拒绝“不同父部门下同名”的合法请求——<b>只应在业务明确要求
     * 全局唯一时使用</b>。
     */
    @Override
    public boolean existsByDepartmentName(String departmentName) {
        if (departmentName == null || departmentName.isBlank()) {
            return false;
        }
        return departmentMapper.exists(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .eq(DepartmentDO::getDepartmentName, departmentName)
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>{@code excludeId} 为 {@code null} 时委托给
     * {@link #existsByDepartmentName(String)}，避免 {@code ne(id, null)} 的
     * 版本兼容问题。
     */
    @Override
    public boolean existsByDepartmentNameAndIdNot(String departmentName, Long excludeId) {
        if (departmentName == null || departmentName.isBlank()) {
            return false;
        }
        if (excludeId == null) {
            return existsByDepartmentName(departmentName);
        }
        return departmentMapper.exists(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .eq(DepartmentDO::getDepartmentName, departmentName)
                        .ne(DepartmentDO::getId, excludeId)
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED));
    }

    // ==================== 存在性判断：子节点 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>显式带 {@code is_deleted = 0}。这一条是
     * {@code deleteDepartment} 子部门守卫的关键——如果过滤条件缺失，
     * 一个已逻辑删除的子部门会永远阻止父部门删除，且用户在前端
     * 看不到这个子部门，无法排查。
     */
    @Override
    public boolean existsByParentId(Long parentId) {
        if (parentId == null) {
            return false;
        }
        return departmentMapper.exists(
                Wrappers.<DepartmentDO>lambdaQuery()
                        .eq(DepartmentDO::getParentId, parentId)
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED));
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
     *   <li>{@code activeDepartmentName} 用 {@code set} 显式赋值，
     *       允许为 {@code null}，不依赖 {@code updateById} 的非空字段策略</li>
     *   <li>业务字段逐项 {@code set}，避免两次 update 的中间态</li>
     * </ul>
     *
     * <p><b>不 set {@code parentId} / {@code ancestors}：</b>移动节点由独立的
     * move 用例处理，{@code DepartmentServiceImpl.updateDepartment} 已用
     * 数据库原值覆盖这两个字段。Repository 层不 set 是第二重保护——
     * 即使调用方绕过 Service 直接传了不同的 {@code parentId}，
     * SQL 里也没有 {@code SET parent_id = ?}。
     *
     * <p><b>审计字段：</b>{@code updateTime} / {@code updateBy} 由调用方在
     * 传入的 {@code department} 上设置，本方法原样写入。如果项目配置了
     * MyBatis-Plus 的 {@code MetaObjectHandler} 自动填充，注意
     * {@code update(null, wrapper)} 不会触发自动填充——此时需要保留
     * {@code department} 上的审计字段值，或在 wrapper 中显式 {@code set}。
     */
    @Override
    public void save(DepartmentDO department) {
        if (department == null) {
            throw new IllegalArgumentException("department 不能为空");
        }

        String departmentName = department.getDepartmentName();
        boolean hasDepartmentName = departmentName != null && !departmentName.isBlank();

        // ---------- 插入 ----------
        if (department.getId() == null) {
            // 辅助列同步设置，保证唯一约束生效
            department.setActiveDepartmentName(hasDepartmentName ? departmentName : null);
            department.setIsDeleted(NOT_DELETED);
            departmentMapper.insert(department);
            return;
        }

        // ---------- 更新 ----------
        // 不 set parentId / ancestors：移动节点由独立的 move 用例处理，
        // Service 层已用数据库原值覆盖，这里不 set 是第二重保护。
        LambdaUpdateWrapper<DepartmentDO> wrapper = Wrappers.<DepartmentDO>lambdaUpdate()
                .eq(DepartmentDO::getId, department.getId())
                .eq(DepartmentDO::getIsDeleted, NOT_DELETED)
                .set(DepartmentDO::getActiveDepartmentName,
                        hasDepartmentName ? departmentName : null)
                .set(DepartmentDO::getDepartmentName, department.getDepartmentName())
                .set(DepartmentDO::getSortOrder, department.getSortOrder())
                .set(DepartmentDO::getManagerUserId, department.getManagerUserId())
                .set(DepartmentDO::getPhoneNumber, department.getPhoneNumber())
                .set(DepartmentDO::getEmail, department.getEmail())
                .set(DepartmentDO::getStatus, department.getStatus())
                .set(DepartmentDO::getUpdateBy, department.getUpdateBy())
                .set(DepartmentDO::getUpdateTime, department.getUpdateTime());

        departmentMapper.update(null, wrapper);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>逐条更新，每条只 {@code set} 一列 {@code ancestors}，
     * 不整行回写，避免覆盖并发修改的其他字段。
     *
     * <p><b>性能说明：</b>子孙量大时应改为 {@code CASE WHEN} 批量更新或
     * 分批 {@code IN} 更新。当前实现适合单次移动影响的子孙数量较小的场景
     * （部门树通常深度有限、单节点子孙数不大）。
     */
    @Override
    public void updateAncestorsBatch(List<DepartmentDO> departments) {
        if (departments == null || departments.isEmpty()) {
            return;
        }
        for (DepartmentDO department : departments) {
            if (department.getId() == null) {
                continue;
            }
            departmentMapper.update(null,
                    Wrappers.<DepartmentDO>lambdaUpdate()
                            .eq(DepartmentDO::getId, department.getId())
                            .eq(DepartmentDO::getIsDeleted, NOT_DELETED)
                            .set(DepartmentDO::getAncestors, department.getAncestors()));
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>必须用 {@link LambdaUpdateWrapper} 显式 {@code set}：
     * {@code updateById} 会忽略 {@code null}，导致
     * {@code active_department_name} 不被清空、唯一约束不释放。
     * 条件里带 {@code is_deleted = 0}，重复删除同一 ID 时第二次
     * 更新 0 行，不会把已删除记录的辅助列又改一遍。
     */
    @Override
    public void deleteById(Long id) {
        if (id == null) {
            return;
        }
        departmentMapper.update(null,
                Wrappers.<DepartmentDO>lambdaUpdate()
                        .eq(DepartmentDO::getId, id)
                        .eq(DepartmentDO::getIsDeleted, NOT_DELETED)
                        .set(DepartmentDO::getIsDeleted, DELETED)
                        .set(DepartmentDO::getActiveDepartmentName, null));
    }
}
