package cn.realm.cloud.system.server.role.internal.application;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.role.events.RoleDeletedEvent;
import cn.realm.cloud.system.server.role.events.RoleStatusChangedEvent;
import cn.realm.cloud.system.server.role.internal.persistence.RoleDO;
import cn.realm.cloud.system.server.role.internal.persistence.RoleDepartmentRepository;
import cn.realm.cloud.system.server.role.internal.persistence.RoleMenuRepository;
import cn.realm.cloud.system.server.role.internal.persistence.RoleRepository;
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
 * {@link RoleService} 的默认实现。
 *
 * <p>负责角色编码/名称的唯一性校验、{@code dataScope} 变更时的部门关联清理、
 * 删除时同模块关联表的清理。数据访问委托给 {@link RoleRepository}、
 * {@link RoleMenuRepository}、{@link RoleDepartmentRepository}，
 * 不直接依赖 MyBatis-Plus。
 *
 * <p><b>校验职责：</b>本类是“分页参数合法性”的最终校验点。
 * HTTP 入口由 {@code RolePageRequest} 拦截，内部调用由本类
 * {@link #page(PageParam, String, Integer)} 显式校验。这与
 * {@code UserServiceImpl} / {@code PositionServiceImpl} 的分层原则一致——
 * Repository / Mapper / 工具类都不做校验，校验集中在 Service 层，
 * 保证无论底层是 MyBatis 还是 JPA，非法参数都在同一层以同一种异常形态暴露。
 *
 * <p><b>删除时的用户角色处理：</b>本模块<b>不</b>清理 {@code iam_user_role}——
 * 该表归 {@code user} 模块所有，本模块查询或写入它会形成
 * {@code role → user} 的反向数据依赖。用户侧的关联由 {@code user} 模块
 * 监听 {@link RoleDeletedEvent} 异步清理，最终一致。在清理完成之前，
 * {@code iam_user_role} 中可能短暂存在指向已删除角色的记录，订阅方
 * 查询时应容忍查不到对应角色的情况，清理逻辑须幂等。
 *
 * <p><b>关于 {@code roleCode}：</b>创建后不可修改。更新方法会用数据库中的
 * 原值覆盖传入值，即使调用方误传也不会生效。
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class RoleServiceImpl implements RoleService {

    /**
     * 状态值：正常
     */
    private static final int STATUS_NORMAL = 0;

    /**
     * 状态值：停用
     */
    private static final int STATUS_DISABLED = 1;

    private final RoleRepository roleRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final RoleDepartmentRepository roleDepartmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 查询 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDO getRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<RoleDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return roleRepository.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RoleDO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return roleRepository.findByIds(ids);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>参数校验：</b>{@code pageSize} 必须在
     * {@code 1 ~ PageParam.MAX_PAGE_SIZE} 之间。HTTP 入口已由
     * {@code RolePageRequest} 的 {@code @Min} / {@code @Max} 拦截，
     * 本方法再校验一次，覆盖内部调用绕过 Controller 的场景——与
     * {@code UserServiceImpl.page} 保持一致。
     *
     * <p><b>为什么不在 Repository 校验：</b>Repository / Mapper 只做
     * 转换和委托，校验上移到 Service 层——这样无论底层是 MyBatis
     * 还是 JPA，非法参数都在同一层以同一种异常形态暴露，不依赖
     * 底层框架的具体行为。
     */
    @Override
    public PageResult<RoleDO> page(PageParam pageParam, String roleName, Integer status) {
        if (pageParam == null) {
            throw new IllegalArgumentException("PageParam 不能为空");
        }
        int pageSize = pageParam.getPageSizeOrDefault();
        if (pageSize <= 0 || pageSize > PageParam.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize 必须在 1 到 " + PageParam.MAX_PAGE_SIZE
                            + " 之间，当前: " + pageSize);
        }
        return roleRepository.page(pageParam, roleName, status);
    }

    // ==================== 命令 ====================

    /**
     * {@inheritDoc}
     *
     * <p>校验顺序：
     * <ol>
     *   <li>{@code roleCode} 在未删除角色中唯一</li>
     *   <li>{@code roleName} 在未删除角色中唯一</li>
     * </ol>
     *
     * <p>唯一性校验前置，但并发下前置校验不可靠——数据库的唯一约束
     * （{@code uk_iam_role_tenant_active_role_code} /
     * {@code uk_iam_role_tenant_active_role_name}）才是最终防线。
     * 并发冲突时抛 {@code DuplicateKeyException}，由全局异常处理器
     * 转为友好的错误信息。
     */
    @Override
    @Transactional
    public Long createRole(RoleDO role) {
        validateRoleCodeUnique(role.getRoleCode(), null);
        validateRoleNameUnique(role.getRoleName(), null);

        roleRepository.save(role);
        log.info("创建角色成功, id={}, code={}, name={}",
                role.getId(), role.getRoleCode(), role.getRoleName());
        return role.getId();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>前提：</b>调用方传入的 {@code role} 通常是“先 {@code getRole}
     * 拿到完整对象、再由 Converter 应用更新字段”的结果。本方法会用
     * 数据库中的原值覆盖 {@code roleCode}，因此即使调用方只传了部分字段，
     * 编码也不会被误改；但其他字段（如 {@code createTime}）会按传入值
     * 整体写入，调用方须保证 {@code role} 是完整对象，而不是一个仅含
     * 待改字段的碎片对象。
     *
     * <p>校验顺序：
     * <ol>
     *   <li>角色存在</li>
     *   <li>{@code roleName} 在未删除角色中唯一，<b>排除自身</b></li>
     *   <li>{@code dataScope} 从自定义变为非自定义时，清理部门关联</li>
     * </ol>
     */
    @Override
    @Transactional
    public void updateRole(RoleDO role) {
        RoleDO existing = getRole(role.getId());

        validateRoleNameUnique(role.getRoleName(), role.getId());

        // roleCode 创建后不可修改——它是业务标识，被 iam_user_role 等
        // 关联表引用时若变更会导致追溯困难。用数据库中的原值覆盖，双重保护。
        role.setRoleCode(existing.getRoleCode());

        // dataScope 从 2（自定义）改为其他值：清理 iam_role_department 中的残留关联。
        // 非自定义范围下这张表的数据无意义，继续保留会导致后续读取到过期部门列表。
        // 从其他值改为 2 时不需要清理——新的部门范围由 assignRoleDepartments 设置。
        if (Objects.equals(existing.getDataScope(), RoleDataScope.CUSTOM)
                && !Objects.equals(role.getDataScope(), RoleDataScope.CUSTOM)) {
            roleDepartmentRepository.deleteByRoleId(role.getId());
            log.info("角色数据范围从自定义改为其他值，清理部门关联, id={}", role.getId());
        }

        roleRepository.save(role);
        log.info("更新角色成功, id={}, name={}", role.getId(), role.getRoleName());
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
     * 的短路必须放在前面，否则拆箱 NPE。这个顺序不能颠倒。
     */
    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != STATUS_NORMAL && status != STATUS_DISABLED)) {
            throw new IllegalArgumentException("非法的状态值: " + status);
        }
        RoleDO role = getRole(id);
        if (Objects.equals(role.getStatus(), status)) {
            return;
        }
        role.setStatus(status);
        roleRepository.save(role);
        eventPublisher.publishEvent(new RoleStatusChangedEvent(id, status));
        log.info("变更角色状态, id={}, status={}", id, status);
    }

    /**
     * {@inheritDoc}
     *
     * <p>清理顺序：先清理同模块的两个关联表，再逻辑删除角色本身，
     * 最后发布事件。在同一事务内，任一步失败都会整体回滚，不会出现
     * “关联清了但角色还在”或“角色删了但关联没清”的中间态。
     *
     * <p>关联表的清理不通过 Repository 的级联，而是显式调用
     * {@code deleteByRoleId}——级联删除在逻辑删除语义下不适用
     * （物理外键的 ON DELETE CASCADE 不会因为逻辑删除而触发）。
     */
    @Override
    @Transactional
    public void deleteRole(Long id) {
        getRole(id);

        // 同模块关联表直接清理：iam_role_menu、iam_role_department
        // 归 role 模块所有，不需要事件机制
        roleMenuRepository.deleteByRoleId(id);
        roleDepartmentRepository.deleteByRoleId(id);

        roleRepository.deleteById(id);

        // 跨模块关联（iam_user_role）由 user 模块监听事件异步清理
        eventPublisher.publishEvent(new RoleDeletedEvent(id));
        log.info("删除角色成功, id={}", id);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验角色编码唯一性。
     *
     * <p>{@code roleCode} 为 {@code null} 或空白时跳过校验——由 DTO 层的
     * {@code @NotBlank} 保证非空，服务层只处理“重复”这一维度。
     * {@code excludeId} 非空时排除该 ID 自身，供更新场景使用
     * （当前 {@code roleCode} 不可修改，更新时不会触发冲突，但保留
     * 该方法签名以便未来放宽约束时复用）。
     *
     * @param roleCode  待校验的角色编码
     * @param excludeId 排除的角色 ID；{@code null} 表示不排除（创建场景）
     * @throws IllegalArgumentException 角色编码已被其他未删除角色占用时抛出
     */
    private void validateRoleCodeUnique(String roleCode, Long excludeId) {
        if (roleCode == null || roleCode.isBlank()) {
            return;
        }
        boolean exists = (excludeId == null)
                ? roleRepository.existsByRoleCode(roleCode)
                : roleRepository.existsByRoleCodeAndIdNot(roleCode, excludeId);
        if (exists) {
            throw new IllegalArgumentException("角色编码已存在: " + roleCode);
        }
    }

    /**
     * 校验角色名称唯一性。
     *
     * <p>与编码一样，{@code roleName} 为 {@code null} 或空白时跳过校验，
     * 由 DTO 层的 {@code @NotBlank} 保证非空。{@code excludeId} 非空时
     * 排除该 ID 自身，供更新场景使用。
     *
     * @param roleName  待校验的角色名称
     * @param excludeId 排除的角色 ID；{@code null} 表示不排除（创建场景）
     * @throws IllegalArgumentException 角色名称已被其他未删除角色占用时抛出
     */
    private void validateRoleNameUnique(String roleName, Long excludeId) {
        if (roleName == null || roleName.isBlank()) {
            return;
        }
        boolean exists = (excludeId == null)
                ? roleRepository.existsByRoleName(roleName)
                : roleRepository.existsByRoleNameAndIdNot(roleName, excludeId);
        if (exists) {
            throw new IllegalArgumentException("角色名称已存在: " + roleName);
        }
    }
}
