package cn.realm.cloud.system.server.organization.internal.persistence;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.organization.internal.persistence.mapper.PositionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link PositionRepository} 的 MyBatis-Plus 实现。
 *
 * <p>MyBatis-Plus 的 {@link PositionMapper} 仅在本类内部使用，不向上泄漏到
 * {@code internal.application}。
 *
 * <p><b>逻辑删除注意：</b>{@code PositionDO} 未标注 {@code @TableLogic}，
 * MyBatis-Plus 不会自动追加 {@code is_deleted = 0} 条件。
 * <b>所有读写操作</b>（包括 {@link #findById}、{@link #findByIds}、
 * {@link #save} 的更新分支）都必须<b>显式</b>加 {@code eq(PositionDO::getIsDeleted, 0)}。
 * 遗漏会导致已删除记录被当作有效数据返回，或在写路径上被“改活”。
 *
 * <p><b>{@code null} 字段注意：</b>{@code updateById} 默认忽略 {@code null}
 * 字段。凡是需要把某列置空的场景（如逻辑删除时清空
 * {@code activePositionCode} / {@code activePositionName}、
 * 更新时把业务列改为空），必须使用 {@link LambdaUpdateWrapper} 显式 {@code set}，
 * 不能依赖实体字段为 {@code null}。
 *
 * <p><b>两个辅助列：</b>{@code PositionDO} 有 {@code activePositionCode} /
 * {@code activePositionName} 两个逻辑删除唯一约束辅助列。
 * {@code save} 的更新分支和 {@code deleteById} 都必须同时维护两个列，
 * 漏一个会导致对应的唯一约束不释放——新建同名/同编码岗位时会撞约束。
 *
 * <p><b>职责边界：本类只做持久化读写，不做参数校验。</b>
 * {@code pageSize} 的取值范围由 {@code PositionServiceImpl.page} 保证，
 * 本类信任入参已合法。
 *
 * <p><b>与 {@code RoleRepositoryImpl} 的对称性：</b>两个类的结构完全一致——
 * 都是两个辅助列、都走 {@code BaseMapperX.selectPage}、
 * 都在 {@code save} 的更新分支不 set 业务标识（{@code roleCode} /
 * {@code positionCode}）。维护者在两者之间切换时不需要重新理解一套规则。
 *
 * @author QI Guang
 */
@Repository
@RequiredArgsConstructor
public class PositionRepositoryImpl implements PositionRepository {

    /**
     * 未删除标记
     */
    private static final int NOT_DELETED = 0;

    /**
     * 已删除标记
     */
    private static final int DELETED = 1;

    private final PositionMapper positionMapper;

    // ==================== 查询 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>不使用 {@code selectById}——{@code PositionDO} 没有
     * {@code @TableLogic}，{@code selectById} 不会过滤逻辑删除，会返回
     * 已删除记录。改用 {@code selectOne} + 显式 {@code is_deleted = 0} 条件。
     */
    @Override
    public Optional<PositionDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(positionMapper.selectOne(
                Wrappers.<PositionDO>lambdaQuery()
                        .eq(PositionDO::getId, id)
                        .eq(PositionDO::getIsDeleted, NOT_DELETED)));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>不使用 {@code selectBatchIds}——同 {@link #findById}，
     * 它不过滤逻辑删除。改用 {@code selectList} + {@code in} + 显式
     * {@code is_deleted = 0}。
     */
    @Override
    public List<PositionDO> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return positionMapper.selectList(
                Wrappers.<PositionDO>lambdaQuery()
                        .in(PositionDO::getId, ids)
                        .eq(PositionDO::getIsDeleted, NOT_DELETED));
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
     * <p>与 {@code UserRepositoryImpl.page} / {@code RoleRepositoryImpl.page}
     * 结构完全一致——五个 Repository 的分页实现统一走 {@code BaseMapperX}，
     * 未来给 {@code selectPage} 加公共逻辑（租户过滤、分页上限兜底）时，
     * 所有模块自动受益。
     */
    @Override
    public PageResult<PositionDO> page(PageParam pageParam, String positionName, Integer status) {
        LambdaQueryWrapper<PositionDO> wrapper = Wrappers.<PositionDO>lambdaQuery()
                .eq(PositionDO::getIsDeleted, NOT_DELETED)
                .like(positionName != null && !positionName.isBlank(),
                        PositionDO::getPositionName, positionName)
                .eq(status != null, PositionDO::getStatus, status)
                .orderByAsc(PositionDO::getSortOrder)
                .orderByAsc(PositionDO::getId);
        return positionMapper.selectPage(pageParam, wrapper);
    }

    // ==================== 存在性判断 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>使用 {@code positionMapper.exists} 生成
     * {@code SELECT 1 ... LIMIT 1} 形式的查询，比 {@code selectCount > 0}
     * 少一次全量计数，在唯一约束这类高频校验上更轻。
     * 需要 MyBatis-Plus 3.5.3.2 及以上版本提供 {@code exists(Wrapper)} 方法。
     */
    @Override
    public boolean existsByPositionCode(String positionCode) {
        if (positionCode == null || positionCode.isBlank()) {
            return false;
        }
        return positionMapper.exists(
                Wrappers.<PositionDO>lambdaQuery()
                        .eq(PositionDO::getPositionCode, positionCode)
                        .eq(PositionDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>{@code excludeId} 为 {@code null} 时委托给
     * {@link #existsByPositionCode(String)}，避免在 wrapper 中拼接
     * 一个无意义的 {@code ne(id, null)} 条件。
     */
    @Override
    public boolean existsByPositionCodeAndIdNot(String positionCode, Long excludeId) {
        if (positionCode == null || positionCode.isBlank()) {
            return false;
        }
        if (excludeId == null) {
            return existsByPositionCode(positionCode);
        }
        return positionMapper.exists(
                Wrappers.<PositionDO>lambdaQuery()
                        .eq(PositionDO::getPositionCode, positionCode)
                        .ne(PositionDO::getId, excludeId)
                        .eq(PositionDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByPositionName(String positionName) {
        if (positionName == null || positionName.isBlank()) {
            return false;
        }
        return positionMapper.exists(
                Wrappers.<PositionDO>lambdaQuery()
                        .eq(PositionDO::getPositionName, positionName)
                        .eq(PositionDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>{@code excludeId} 为 {@code null} 时委托给
     * {@link #existsByPositionName(String)}，避免 {@code ne(id, null)} 的
     * 版本兼容问题。
     */
    @Override
    public boolean existsByPositionNameAndIdNot(String positionName, Long excludeId) {
        if (positionName == null || positionName.isBlank()) {
            return false;
        }
        if (excludeId == null) {
            return existsByPositionName(positionName);
        }
        return positionMapper.exists(
                Wrappers.<PositionDO>lambdaQuery()
                        .eq(PositionDO::getPositionName, positionName)
                        .ne(PositionDO::getId, excludeId)
                        .eq(PositionDO::getIsDeleted, NOT_DELETED));
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
     *   <li>{@code activePositionCode} / {@code activePositionName} 用
     *       {@code set} 显式赋值，允许为 {@code null}，不依赖
     *       {@code updateById} 的非空字段策略</li>
     *   <li>业务字段逐项 {@code set}，避免两次 update 的中间态</li>
     * </ul>
     *
     * <p><b>不 set {@code positionCode}：</b>它是业务标识，创建后不可修改。
     * {@code PositionServiceImpl.updatePosition} 已用数据库原值覆盖传入值，
     * Repository 层不 set 是第二重保护——即使调用方绕过 Service 直接传了
     * 不同的 {@code positionCode}，SQL 里也没有 {@code SET position_code = ?}。
     * 这与 {@code RoleRepositoryImpl} 不 set {@code roleCode}、
     * {@code UserRepositoryImpl} 不 set {@code username} 是同一原则：
     * 业务标识的不可变性，Repository 层也守一道。
     *
     * <p><b>审计字段：</b>{@code updateTime} / {@code updateBy} 由调用方在
     * 传入的 {@code position} 上设置，本方法原样写入。如果项目配置了
     * MyBatis-Plus 的 {@code MetaObjectHandler} 自动填充，注意
     * {@code update(null, wrapper)} 不会触发自动填充——此时需要保留
     * {@code position} 上的审计字段值，或在 wrapper 中显式 {@code set}。
     */
    @Override
    public void save(PositionDO position) {
        if (position == null) {
            throw new IllegalArgumentException("position 不能为空");
        }

        String positionCode = position.getPositionCode();
        boolean hasPositionCode = positionCode != null && !positionCode.isBlank();
        String positionName = position.getPositionName();
        boolean hasPositionName = positionName != null && !positionName.isBlank();

        // ---------- 插入 ----------
        if (position.getId() == null) {
            // 两个辅助列同步设置，保证唯一约束生效
            position.setActivePositionCode(hasPositionCode ? positionCode : null);
            position.setActivePositionName(hasPositionName ? positionName : null);
            position.setIsDeleted(NOT_DELETED);
            positionMapper.insert(position);
            return;
        }

        // ---------- 更新 ----------
        // 两个辅助列都必须显式 set，漏一个会导致对应的唯一约束不释放。
        // 不 set positionCode：创建后不可修改，Service 层已用原值覆盖，
        // Repository 层不 set 是第二重保护。
        LambdaUpdateWrapper<PositionDO> wrapper = Wrappers.<PositionDO>lambdaUpdate()
                .eq(PositionDO::getId, position.getId())
                .eq(PositionDO::getIsDeleted, NOT_DELETED)
                .set(PositionDO::getActivePositionCode,
                        hasPositionCode ? positionCode : null)
                .set(PositionDO::getActivePositionName,
                        hasPositionName ? positionName : null)
                .set(PositionDO::getPositionName, position.getPositionName())
                .set(PositionDO::getSortOrder, position.getSortOrder())
                .set(PositionDO::getStatus, position.getStatus())
                .set(PositionDO::getDescription, position.getDescription())
                .set(PositionDO::getUpdateBy, position.getUpdateBy())
                .set(PositionDO::getUpdateTime, position.getUpdateTime());

        positionMapper.update(null, wrapper);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>必须用 {@link LambdaUpdateWrapper} 显式 {@code set}：
     * {@code updateById} 会忽略 {@code null}，导致
     * {@code active_position_code} / {@code active_position_name}
     * 不被清空、唯一约束不释放。条件里带 {@code is_deleted = 0}，
     * 重复删除同一 ID 时第二次更新 0 行，不会把已删除记录的辅助列又改一遍。
     */
    @Override
    public void deleteById(Long id) {
        if (id == null) {
            return;
        }
        positionMapper.update(null,
                Wrappers.<PositionDO>lambdaUpdate()
                        .eq(PositionDO::getId, id)
                        .eq(PositionDO::getIsDeleted, NOT_DELETED)
                        .set(PositionDO::getIsDeleted, DELETED)
                        .set(PositionDO::getActivePositionCode, null)
                        .set(PositionDO::getActivePositionName, null));
    }
}
