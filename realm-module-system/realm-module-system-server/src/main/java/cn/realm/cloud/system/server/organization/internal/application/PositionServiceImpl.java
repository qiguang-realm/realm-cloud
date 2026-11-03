package cn.realm.cloud.system.server.organization.internal.application;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.organization.events.PositionDeletedEvent;
import cn.realm.cloud.system.server.organization.events.PositionStatusChangedEvent;
import cn.realm.cloud.system.server.organization.internal.persistence.PositionDO;
import cn.realm.cloud.system.server.organization.internal.persistence.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link PositionService} 的默认实现。
 *
 * <p>负责岗位编码/名称的唯一性校验、状态值校验。数据访问委托给
 * {@link PositionRepository}，不直接依赖 MyBatis-Plus。
 *
 * <p><b>校验职责：</b>本类是“分页参数合法性”的最终校验点。HTTP 入口
 * 由 {@code PositionPageRequest} 拦截，内部调用由本类
 * {@link #page(PageParam, String, Integer)} 显式校验。这与
 * {@code UserServiceImpl} / {@code RoleServiceImpl} 的分层原则一致——
 * Repository / Mapper / 工具类都不做校验，校验集中在 Service 层，
 * 保证无论底层是 MyBatis 还是 JPA，非法参数都在同一层以同一种异常形态暴露。
 *
 * <p><b>删除时的用户引用处理：</b>本模块<b>不</b>同步校验岗位是否被用户引用——
 * {@code iam_user_position} 归 {@code user} 模块所有，本模块查询它会形成
 * {@code organization → user} 的反向数据依赖。用户侧的关联由 {@code user}
 * 模块监听 {@link PositionDeletedEvent} 异步清理，最终一致。在清理完成之前，
 * {@code iam_user_position} 中可能短暂存在指向已删除岗位的记录，订阅方
 * 查询时应容忍查不到对应岗位的情况，清理逻辑须幂等。
 *
 * <p><b>关于 {@code positionCode}：</b>创建后不可修改。更新方法会用数据库中的
 * 原值覆盖传入值，即使调用方误传也不会生效。
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class PositionServiceImpl implements PositionService {

    /**
     * 状态值：正常
     */
    private static final int STATUS_NORMAL = 0;

    /**
     * 状态值：停用
     */
    private static final int STATUS_DISABLED = 1;

    private final PositionRepository positionRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 查询 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionDO getPosition(Long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("岗位不存在: " + id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<PositionDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return positionRepository.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PositionDO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return positionRepository.findByIds(ids);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>参数校验：</b>{@code pageSize} 必须在
     * {@code 1 ~ PageParam.MAX_PAGE_SIZE} 之间。HTTP 入口已由
     * {@code PositionPageRequest} 的 {@code @Min} / {@code @Max} 拦截，
     * 本方法再校验一次，覆盖内部调用绕过 Controller 的场景——与
     * {@code UserServiceImpl.page} / {@code RoleServiceImpl.page} 保持一致。
     *
     * <p><b>为什么不在 Repository 校验：</b>Repository / Mapper 只做
     * 转换和委托，校验上移到 Service 层——这样无论底层是 MyBatis
     * 还是 JPA，非法参数都在同一层以同一种异常形态暴露。
     */
    @Override
    public PageResult<PositionDO> page(PageParam pageParam, String positionName, Integer status) {
        if (pageParam == null) {
            throw new IllegalArgumentException("PageParam 不能为空");
        }
        int pageSize = pageParam.getPageSizeOrDefault();
        if (pageSize <= 0 || pageSize > PageParam.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize 必须在 1 到 " + PageParam.MAX_PAGE_SIZE
                            + " 之间，当前: " + pageSize);
        }
        return positionRepository.page(pageParam, positionName, status);
    }

    // ==================== 命令 ====================

    /**
     * {@inheritDoc}
     *
     * <p>校验顺序：
     * <ol>
     *   <li>{@code positionCode} 在未删除岗位中唯一</li>
     *   <li>{@code positionName} 在未删除岗位中唯一</li>
     * </ol>
     * 先校验编码再校验名称——编码是业务标识，冲突时应该优先提示编码，
     * 因为用户通常先设编码再设名称。
     */
    @Override
    @Transactional
    public Long createPosition(PositionDO position) {
        validatePositionCodeUnique(position.getPositionCode(), null);
        validatePositionNameUnique(position.getPositionName(), null);

        positionRepository.save(position);
        log.info("创建岗位成功, id={}, code={}, name={}",
                position.getId(), position.getPositionCode(), position.getPositionName());
        return position.getId();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>前提：</b>调用方传入的 {@code position} 通常是“先
     * {@code getPosition} 拿到完整对象、再由 Converter 应用更新字段”的结果。
     * 本方法会用数据库中的原值覆盖 {@code positionCode}，因此即使调用方
     * 只传了部分字段，编码也不会被误改；但其他字段（如 {@code createTime}）
     * 会按传入值整体写入，调用方须保证 {@code position} 是完整对象，
     * 而不是一个仅含待改字段的碎片对象。
     *
     * <p>校验顺序：
     * <ol>
     *   <li>岗位存在</li>
     *   <li>{@code positionName} 在未删除岗位中唯一，<b>排除自身</b></li>
     * </ol>
     */
    @Override
    @Transactional
    public void updatePosition(PositionDO position) {
        PositionDO existing = getPosition(position.getId());

        validatePositionNameUnique(position.getPositionName(), position.getId());

        // positionCode 创建后不可修改——它是业务标识，被 iam_user_position 等
        // 关联表引用时若变更会导致追溯困难。用数据库中的原值覆盖，双重保护。
        // Repository 层的 save 也不 set positionCode，是第二重保护。
        position.setPositionCode(existing.getPositionCode());

        positionRepository.save(position);
        log.info("更新岗位成功, id={}, name={}",
                position.getId(), position.getPositionName());
    }

    /**
     * {@inheritDoc}
     *
     * <p>状态未实际变化时不写库、不发布事件，避免订阅方收到
     * “状态从 1 变为 1” 这类无意义的通知。
     *
     * <p><b>关于 {@code status == null} 判断的顺序：</b>
     * {@code status != STATUS_NORMAL} 中 {@code status} 是 {@code Integer}、
     * {@code STATUS_NORMAL} 是 {@code int}——会拆箱。{@code status == null}
     * 的短路必须放在前面，否则拆箱 NPE。顺序不能颠倒。
     */
    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        // 顺序不能颠倒：status == null 必须在前面，避免后续拆箱 NPE
        if (status == null || (status != STATUS_NORMAL && status != STATUS_DISABLED)) {
            throw new IllegalArgumentException("非法的状态值: " + status);
        }
        PositionDO position = getPosition(id);
        // 状态未变化时短路，不写库、不发事件
        if (Objects.equals(position.getStatus(), status)) {
            return;
        }
        position.setStatus(status);
        positionRepository.save(position);
        eventPublisher.publishEvent(new PositionStatusChangedEvent(id, status));
        log.info("变更岗位状态, id={}, status={}", id, status);
    }

    /**
     * {@inheritDoc}
     *
     * <p>执行顺序：
     * <ol>
     *   <li>校验岗位存在（不存在时抛异常）</li>
     *   <li>逻辑删除岗位本身</li>
     *   <li>发布 {@code PositionDeletedEvent}——供 {@code user} 模块
     *       异步清理用户对岗位的关联</li>
     * </ol>
     * 前两步在同一事务内，任一步失败都会整体回滚。第 3 步的事件在
     * 事务内发布，但订阅方用 {@code @ApplicationModuleListener} 消费，
     * 实际执行在事务提交之后。
     */
    @Override
    @Transactional
    public void deletePosition(Long id) {
        getPosition(id);
        positionRepository.deleteById(id);
        // 跨模块关联（iam_user_position）由 user 模块监听事件异步清理
        eventPublisher.publishEvent(new PositionDeletedEvent(id));
        log.info("删除岗位成功, id={}", id);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验岗位编码唯一性。
     *
     * <p>{@code positionCode} 为 {@code null} 或空白时跳过校验——
     * 由 DTO 层的 {@code @NotBlank} 保证非空，服务层只处理“重复”这一维度。
     * {@code excludeId} 非空时排除该 ID 自身，供更新场景使用
     * （当前 {@code positionCode} 不可修改，更新时不会触发冲突，但保留
     * 该方法签名以便未来放宽约束时复用）。
     *
     * <p><b>并发下前置校验不可靠：</b>数据库的唯一约束
     * {@code uk_iam_position_tenant_active_position_code} 才是最终防线。
     *
     * @param positionCode 待校验的岗位编码
     * @param excludeId    排除的岗位 ID；{@code null} 表示不排除（创建场景）
     * @throws IllegalArgumentException 岗位编码已被其他未删除岗位占用时抛出
     */
    private void validatePositionCodeUnique(String positionCode, Long excludeId) {
        if (positionCode == null || positionCode.isBlank()) {
            return;
        }
        boolean exists = (excludeId == null)
                ? positionRepository.existsByPositionCode(positionCode)
                : positionRepository.existsByPositionCodeAndIdNot(positionCode, excludeId);
        if (exists) {
            throw new IllegalArgumentException("岗位编码已存在: " + positionCode);
        }
    }

    /**
     * 校验岗位名称唯一性。
     *
     * <p>与编码一样，{@code positionName} 为 {@code null} 或空白时跳过校验，
     * 由 DTO 层的 {@code @NotBlank} 保证非空。{@code excludeId} 非空时
     * 排除该 ID 自身，供更新场景使用。
     *
     * <p><b>并发下前置校验不可靠：</b>数据库的唯一约束
     * {@code uk_iam_position_tenant_active_position_name} 才是最终防线。
     *
     * @param positionName 待校验的岗位名称
     * @param excludeId    排除的岗位 ID；{@code null} 表示不排除（创建场景）
     * @throws IllegalArgumentException 岗位名称已被其他未删除岗位占用时抛出
     */
    private void validatePositionNameUnique(String positionName, Long excludeId) {
        if (positionName == null || positionName.isBlank()) {
            return;
        }
        boolean exists = (excludeId == null)
                ? positionRepository.existsByPositionName(positionName)
                : positionRepository.existsByPositionNameAndIdNot(positionName, excludeId);
        if (exists) {
            throw new IllegalArgumentException("岗位名称已存在: " + positionName);
        }
    }
}
