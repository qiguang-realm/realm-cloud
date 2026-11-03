package cn.realm.cloud.system.server.role.internal.persistence;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.role.internal.persistence.mapper.RoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link RoleRepository} 的 MyBatis-Plus 实现。
 *
 * <p>MyBatis-Plus 的 {@link RoleMapper} 仅在本类内部使用，不向上泄漏到
 * {@code internal.application}。
 *
 * <p><b>逻辑删除注意：</b>{@code RoleDO} 未标注 {@code @TableLogic}，
 * MyBatis-Plus 不会自动追加 {@code is_deleted = 0} 条件。
 * <b>所有读写操作</b>（包括 {@link #findById}、{@link #findByIds}、
 * {@link #save} 的更新分支）都必须<b>显式</b>加 {@code eq(RoleDO::getIsDeleted, 0)}。
 * 遗漏会导致已删除记录被当作有效数据返回，或在写路径上被“改活”。
 *
 * <p><b>{@code null} 字段注意：</b>{@code updateById} 默认忽略 {@code null}
 * 字段。凡是需要把某列置空的场景（如逻辑删除时清空
 * {@code activeRoleCode} / {@code activeRoleName}），必须使用
 * {@link LambdaUpdateWrapper} 显式 {@code set}，不能依赖实体字段为 {@code null}。
 *
 * <p><b>两个辅助列：</b>{@code RoleDO} 有 {@code activeRoleCode} /
 * {@code activeRoleName} 两个逻辑删除唯一约束辅助列。
 * {@code save} 的更新分支和 {@code deleteById} 都必须同时维护两个列，
 * 漏一个会导致对应的唯一约束不释放——新建同名/同编码角色时会撞约束。
 *
 * <p><b>职责边界：本类只做持久化读写，不做参数校验。</b>
 * {@code pageSize} 的取值范围由 {@code RoleServiceImpl.page} 保证，
 * 本类信任入参已合法。
 *
 * <p><b>乐观锁：</b>{@code RoleDO.version} 未标注 {@code @Version}，
 * 当前<b>不启用</b>乐观锁。本类用 {@link LambdaUpdateWrapper} 显式 {@code set}
 * 字段，这种写法本身也不会触发乐观锁——即使未来加了 {@code @Version}，
 * 也需要改用 {@code updateById(entity)} 才能生效。如需并发保护，
 * 在 wrapper 中手动加 {@code eq(version, expectedVersion)} 条件。
 *
 * @author QI Guang
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    /**
     * 未删除标记
     */
    private static final int NOT_DELETED = 0;

    /**
     * 已删除标记
     */
    private static final int DELETED = 1;

    private final RoleMapper roleMapper;

    // ==================== 查询 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>不使用 {@code selectById}——{@code RoleDO} 没有
     * {@code @TableLogic}，{@code selectById} 不会过滤逻辑删除，会返回
     * 已删除记录。改用 {@code selectOne} + 显式 {@code is_deleted = 0} 条件。
     */
    @Override
    public Optional<RoleDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roleMapper.selectOne(
                Wrappers.<RoleDO>lambdaQuery()
                        .eq(RoleDO::getId, id)
                        .eq(RoleDO::getIsDeleted, NOT_DELETED)));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>不使用 {@code selectBatchIds}——同 {@link #findById}，
     * 它不过滤逻辑删除。改用 {@code selectList} + {@code in} + 显式
     * {@code is_deleted = 0}。
     */
    @Override
    public List<RoleDO> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(
                Wrappers.<RoleDO>lambdaQuery()
                        .in(RoleDO::getId, ids)
                        .eq(RoleDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>直接委托 {@code BaseMapperX.selectPage(PageParam, Wrapper)}
     * ——这是 {@code BaseMapperX} 提供的 default 方法，内部完成三步：
     * <ol>
     *   <li>{@code MyBatisPageUtils.toPage(pageParam)} 构造 {@code Page}</li>
     *   <li>{@code selectPage(page, wrapper)} 执行查询，分页拦截器生成
     *       {@code LIMIT} 与 {@code COUNT}</li>
     *   <li>{@code MyBatisPageUtils.toPageResult(page)} 转换为框架无关的
     *       {@link PageResult}</li>
     * </ol>
     *
     * <p>与 {@code UserRepositoryImpl.page} / {@code PositionRepositoryImpl.page}
     * 结构完全一致——五个 Repository 的分页实现统一走 {@code BaseMapperX}，
     * 未来给 {@code selectPage} 加公共逻辑（租户过滤、分页上限兜底）时，
     * 所有模块自动受益。
     */
    @Override
    public PageResult<RoleDO> page(PageParam pageParam, String roleName, Integer status) {
        LambdaQueryWrapper<RoleDO> wrapper = Wrappers.<RoleDO>lambdaQuery()
                .eq(RoleDO::getIsDeleted, NOT_DELETED)
                .like(roleName != null && !roleName.isBlank(),
                        RoleDO::getRoleName, roleName)
                .eq(status != null, RoleDO::getStatus, status)
                .orderByAsc(RoleDO::getSortOrder)
                .orderByAsc(RoleDO::getId);
        return roleMapper.selectPage(pageParam, wrapper);
    }

    // ==================== 存在性判断 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>使用 {@code roleMapper.exists} 生成
     * {@code SELECT 1 ... LIMIT 1} 形式的查询，比 {@code selectCount > 0}
     * 少一次全量计数，在唯一约束这类高频校验上更轻。
     * 需要 MyBatis-Plus 3.5.3.2 及以上版本提供 {@code exists(Wrapper)} 方法。
     */
    @Override
    public boolean existsByRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return roleMapper.exists(
                Wrappers.<RoleDO>lambdaQuery()
                        .eq(RoleDO::getRoleCode, roleCode)
                        .eq(RoleDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>{@code excludeId} 为 {@code null} 时委托给
     * {@link #existsByRoleCode(String)}，避免在 wrapper 中拼接
     * 一个无意义的 {@code ne(id, null)} 条件。
     */
    @Override
    public boolean existsByRoleCodeAndIdNot(String roleCode, Long excludeId) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        if (excludeId == null) {
            return existsByRoleCode(roleCode);
        }
        return roleMapper.exists(
                Wrappers.<RoleDO>lambdaQuery()
                        .eq(RoleDO::getRoleCode, roleCode)
                        .ne(RoleDO::getId, excludeId)
                        .eq(RoleDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }
        return roleMapper.exists(
                Wrappers.<RoleDO>lambdaQuery()
                        .eq(RoleDO::getRoleName, roleName)
                        .eq(RoleDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByRoleNameAndIdNot(String roleName, Long excludeId) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }
        if (excludeId == null) {
            return existsByRoleName(roleName);
        }
        return roleMapper.exists(
                Wrappers.<RoleDO>lambdaQuery()
                        .eq(RoleDO::getRoleName, roleName)
                        .ne(RoleDO::getId, excludeId)
                        .eq(RoleDO::getIsDeleted, NOT_DELETED));
    }

    // ==================== 写入 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>更新分支的处理策略：</b>不使用 {@code updateById}，而是用
     * {@link LambdaUpdateWrapper} 一次性完成「条件 + 两个辅助列 + 业务字段」：
     * <ul>
     *   <li>条件里显式带 {@code id} 和 {@code is_deleted = 0}，
     *       避免把已逻辑删除的记录改活</li>
     *   <li>{@code activeRoleCode} / {@code activeRoleName} 用
     *       {@code set} 显式赋值，允许为 {@code null}，不依赖
     *       {@code updateById} 的非空字段策略</li>
     *   <li>业务字段逐项 {@code set}，避免两次 update 的中间态</li>
     * </ul>
     *
     * <p><b>不更新 {@code roleCode}：</b>虽然 {@code RoleServiceImpl.updateRole}
     * 会先用数据库原值覆盖 {@code roleCode}，本方法仍不 {@code set} 它——
     * 这是双重保护：即使调用方绕过 Service 直接传了一个不同的 {@code roleCode}，
     * 本方法也不会改掉登录标识。
     *
     * <p><b>审计字段：</b>{@code updateTime} / {@code updateBy} 由调用方在
     * 传入的 {@code role} 上设置，本方法原样写入。如果项目配置了
     * MyBatis-Plus 的 {@code MetaObjectHandler} 自动填充，注意
     * {@code update(null, wrapper)} 不会触发自动填充——此时需要保留
     * {@code role} 上的审计字段值，或在 wrapper 中显式 {@code set}。
     */
    @Override
    public void save(RoleDO role) {
        if (role == null) {
            throw new IllegalArgumentException("role 不能为空");
        }

        String roleCode = role.getRoleCode();
        boolean hasRoleCode = roleCode != null && !roleCode.isBlank();
        String roleName = role.getRoleName();
        boolean hasRoleName = roleName != null && !roleName.isBlank();

        // ---------- 插入 ----------
        if (role.getId() == null) {
            role.setActiveRoleCode(hasRoleCode ? roleCode : null);
            role.setActiveRoleName(hasRoleName ? roleName : null);
            role.setIsDeleted(NOT_DELETED);
            roleMapper.insert(role);
            return;
        }

        // ---------- 更新 ----------
        // 两个辅助列都必须显式 set，漏一个会导致对应的唯一约束不释放。
        // 不 set roleCode：创建后不可修改，Service 层已用原值覆盖，
        // Repository 层不 set 是第二重保护。
        LambdaUpdateWrapper<RoleDO> wrapper = Wrappers.<RoleDO>lambdaUpdate()
                .eq(RoleDO::getId, role.getId())
                .eq(RoleDO::getIsDeleted, NOT_DELETED)
                .set(RoleDO::getActiveRoleCode,
                        hasRoleCode ? roleCode : null)
                .set(RoleDO::getActiveRoleName,
                        hasRoleName ? roleName : null)
                .set(RoleDO::getRoleName, role.getRoleName())
                .set(RoleDO::getSortOrder, role.getSortOrder())
                .set(RoleDO::getDataScope, role.getDataScope())
                .set(RoleDO::getStatus, role.getStatus())
                .set(RoleDO::getDescription, role.getDescription())
                .set(RoleDO::getUpdateBy, role.getUpdateBy())
                .set(RoleDO::getUpdateTime, role.getUpdateTime());

        roleMapper.update(null, wrapper);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>必须用 {@link LambdaUpdateWrapper} 显式 {@code set}：
     * {@code updateById} 会忽略 {@code null}，导致
     * {@code active_role_code} / {@code active_role_name}
     * 不被清空、唯一约束不释放。条件里带 {@code is_deleted = 0}，
     * 重复删除同一 ID 时第二次更新 0 行，不会把已删除记录的辅助列又改一遍。
     */
    @Override
    public void deleteById(Long id) {
        if (id == null) {
            return;
        }
        roleMapper.update(null,
                Wrappers.<RoleDO>lambdaUpdate()
                        .eq(RoleDO::getId, id)
                        .eq(RoleDO::getIsDeleted, NOT_DELETED)
                        .set(RoleDO::getIsDeleted, DELETED)
                        .set(RoleDO::getActiveRoleCode, null)
                        .set(RoleDO::getActiveRoleName, null));
    }
}
