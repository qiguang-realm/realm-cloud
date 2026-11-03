package cn.realm.cloud.system.server.organization.internal.application;

import cn.realm.cloud.system.server.organization.events.DepartmentDeletedEvent;
import cn.realm.cloud.system.server.organization.events.DepartmentStatusChangedEvent;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.converter.DepartmentAdminConverter;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.DepartmentResponse;
import cn.realm.cloud.system.server.organization.internal.persistence.DepartmentDO;
import cn.realm.cloud.system.server.organization.internal.persistence.DepartmentRepository;
import cn.realm.cloud.system.server.user.UserFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link DepartmentService} 的默认实现。
 *
 * <p>负责 {@code ancestors} 的拼接、删除前的子部门校验、部门名称唯一性校验
 * （<b>同级唯一</b>）、{@code managerUserId} 的存在性校验。数据访问委托给
 * {@link DepartmentRepository}，不直接依赖 MyBatis-Plus。
 *
 * <p><b>部门名称唯一性：同级唯一。</b>与 DDL 的
 * {@code uk_iam_department_tenant_parent_name}
 * {@code (tenant_id, parent_id, active_department_name)} 对齐——
 * 同一父部门下名称唯一，不同父部门下可同名。校验通过
 * {@code DepartmentRepository.existsByParentIdAndDepartmentName} 完成。
 *
 * <p><b>删除时的用户引用处理：</b>本模块<b>不</b>同步校验部门是否被用户引用——
 * 那会形成 {@code organization → user} 的反向数据依赖（用户表的
 * {@code department_id} 归 {@code user} 模块所有）。用户侧的引用由
 * {@code user} 模块监听 {@link DepartmentDeletedEvent} 异步清理，最终一致。
 *
 * <p><b>跨模块依赖：</b>{@code organization → user} 的单向同步依赖，
 * 用于 {@link UserFacade#existsById(Long)} 校验 {@code managerUserId}。
 * {@code user} 模块不反向同步依赖本模块的 {@code internal} 类型。
 *
 * <p><b>关于 {@code ancestors}：</b>逗号分隔的祖先 ID 链，顶级部门为
 * {@code "0"}。该字段永远由本类计算，不接受外部输入。
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class DepartmentServiceImpl implements DepartmentService {

    /**
     * 顶级部门的固定祖先链前缀。
     *
     * <p>格式为逗号分隔的祖先 ID 链，顶级部门没有祖先，
     * 用 {@code "0"} 作为哨兵值，使子孙查询 {@code LIKE '0,%'} 能覆盖
     * 所有非顶级部门。
     */
    private static final String ROOT_ANCESTORS = "0";

    /**
     * 顶级部门的父节点 ID。
     *
     * <p>{@code parentId} 为 {@code null} 或 {@code 0} 时都按顶级处理。
     * 数据库 DDL 中顶级部门的 {@code parent_id} 为 {@code 0}，
     * 这里用常量与 DDL 保持一致，避免硬编码 {@code 0L} 散落在代码里。
     */
    private static final long ROOT_PARENT_ID = 0L;

    /**
     * 状态值：正常
     */
    private static final int STATUS_NORMAL = 0;

    /**
     * 状态值：停用
     */
    private static final int STATUS_DISABLED = 1;

    private final DepartmentRepository departmentRepository;

    /**
     * 用户门面，用于校验 {@code managerUserId} 的有效性。
     *
     * <p>这是 {@code organization → user} 的同步依赖。方向单向：
     * {@code user} 模块不反向同步依赖本模块的内部类型，对部门的使用
     * 通过 {@code OrganizationFacade} 或事件完成。
     */
    private final UserFacade userFacade;

    private final ApplicationEventPublisher eventPublisher;

    // ==================== 查询 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public DepartmentDO getDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在: " + id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<DepartmentDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return departmentRepository.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DepartmentDO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return departmentRepository.findByIds(ids);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>为什么在 Service 层重排：</b>Repository 的 {@code findAll}
     * 已按 {@code sort_order}、{@code id} 升序排列，但 MySQL 的
     * {@code ORDER BY} 对 {@code NULL} 的处理是“排最前”，与业务期望的
     * “排末尾”相反。Service 层用 {@code Comparator.nullsLast} 修正这个差异，
     * 把“排序语义”固定在应用层，不依赖数据库方言的具体行为。
     *
     * <p>如果确认数据库层面已经用 {@code ORDER BY sort_order IS NULL, sort_order}
     * 处理了 {@code NULL} 排序，这里的重排可以移除，省一次遍历。
     * 当前保留作为防御。
     */
    @Override
    public List<DepartmentDO> listAll() {
        return departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        DepartmentDO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>与 {@link #listAll()} 一样做防御性排序，原因相同——
     * 数据库对 {@code NULL} 的排序行为与 Java 的 {@code nullsLast}
     * 不一致，Service 层统一修正。
     */
    @Override
    public List<DepartmentDO> listByParentId(Long parentId) {
        if (parentId == null) {
            return List.of();
        }
        return departmentRepository.findByParentId(parentId).stream()
                .sorted(Comparator.comparing(
                        DepartmentDO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>组装步骤：</b>
     * <ol>
     *   <li>调用 {@link #listAll()} 拿到已按 {@code sortOrder} 排序的扁平列表</li>
     *   <li>按 {@code parentId} 分组；{@code null} 归一为 {@link #ROOT_PARENT_ID}。
     *       使用 {@link LinkedHashMap} 保持分组后的顺序与输入一致</li>
     *   <li>从根节点开始递归构造 {@link DepartmentResponse}——自底向上，
     *       先递归得到子节点列表，再构造父节点，因为 {@code DepartmentResponse}
     *       是不可变 {@code record}，不能“先建父节点再往里塞子节点”</li>
     * </ol>
     *
     * <p><b>孤儿节点处理：</b>{@code parentId} 指向不存在或已删除部门的节点
     * 会被丢弃——它们不在根节点的 {@code byParent} 分组里，递归时永远不会
     * 被访问到。丢弃而不是挂到根上，避免掩盖数据不一致的问题。
     */
    @Override
    public List<DepartmentResponse> listTree() {
        List<DepartmentDO> all = listAll();
        if (all.isEmpty()) {
            return List.of();
        }
        // 按 parentId 分组，null 归一为 ROOT_PARENT_ID。
        // LinkedHashMap 保持分组后的顺序与 listAll() 返回的顺序一致。
        Map<Long, List<DepartmentDO>> byParent = all.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getParentId() == null ? ROOT_PARENT_ID : d.getParentId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return buildChildren(ROOT_PARENT_ID, byParent);
    }

    // ==================== 命令 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>校验顺序：</b>
     * <ol>
     *   <li>{@code departmentName} 在同一父部门下唯一（最轻的本地查询）</li>
     *   <li>{@code managerUserId} 非空时对应用户存在（跨模块调用，较重）</li>
     *   <li>父部门存在——由 {@link #resolveAncestors(Long)} 内部校验</li>
     * </ol>
     * 先校验“轻”的，尽早失败，不浪费一次跨模块查询。
     *
     * <p><b>为什么 {@code ancestors} 由服务端计算：</b>它是持久化辅助列，
     * 格式为逗号分隔的祖先 ID 链，如果让调用方传入，容易被拼错或漏拼。
     * 由服务端根据 {@code parentId} 统一计算，保证全表数据的一致性。
     */
    @Override
    @Transactional
    public Long createDepartment(DepartmentDO department) {
        validateDepartmentNameUnique(
                department.getParentId(), department.getDepartmentName(), null);
        validateManagerUser(department.getManagerUserId());

        // ancestors 由服务端计算，覆盖调用方传入的任何值
        department.setAncestors(resolveAncestors(department.getParentId()));

        departmentRepository.save(department);
        log.info("创建部门成功, id={}, name={}",
                department.getId(), department.getDepartmentName());
        return department.getId();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>前提：</b>调用方传入的 {@code department} 通常是“先
     * {@code getDepartment} 拿到完整对象、再由 Converter 应用更新字段”
     * 的结果。本方法会用数据库中的原值覆盖 {@code parentId} 和
     * {@code ancestors}，因此即使调用方只传了部分字段，这两个字段
     * 也不会被误改。
     *
     * <p><b>校验顺序：</b>
     * <ol>
     *   <li>部门存在</li>
     *   <li>{@code departmentName} 在同一父部门下唯一，排除自身</li>
     *   <li>{@code managerUserId} 非空时对应用户存在</li>
     * </ol>
     */
    @Override
    @Transactional
    public void updateDepartment(DepartmentDO department) {
        DepartmentDO existing = getDepartment(department.getId());

        // 名称唯一性校验用 existing.getParentId()——部门不通过普通编辑移动，
        // 父节点保持数据库原值。排除自身 ID，避免把未改动的名称判为冲突。
        validateDepartmentNameUnique(
                existing.getParentId(), department.getDepartmentName(), department.getId());
        validateManagerUser(department.getManagerUserId());

        // 不允许改 parentId / ancestors —— 移动节点会触发整棵子树 ancestors 重算，
        // 由独立的 move 用例处理，避免与普通编辑混在一起。
        // 用数据库原值覆盖，即使调用方误传也不会生效。
        department.setParentId(existing.getParentId());
        department.setAncestors(existing.getAncestors());

        departmentRepository.save(department);
        log.info("更新部门成功, id={}, name={}",
                department.getId(), department.getDepartmentName());
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>状态未实际变化时不写库、不发布事件：</b>避免订阅方收到
     * “状态从 1 变为 1”这类无意义的通知，也避免产生无意义的 binlog
     * 和审计记录。
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
        DepartmentDO department = getDepartment(id);
        // 状态未变化时短路，不写库、不发事件
        if (Objects.equals(department.getStatus(), status)) {
            return;
        }
        department.setStatus(status);
        departmentRepository.save(department);
        eventPublisher.publishEvent(new DepartmentStatusChangedEvent(id, status));
        log.info("变更部门状态, id={}, status={}", id, status);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>执行顺序：</b>
     * <ol>
     *   <li>校验部门存在（不存在时抛异常）</li>
     *   <li>校验不存在未删除的子部门——有子部门时拒绝删除，
     *       避免留下孤儿节点和断裂的 {@code ancestors} 链</li>
     *   <li>逻辑删除部门本身</li>
     *   <li>发布 {@code DepartmentDeletedEvent}——供 {@code user} 模块
     *       异步清理用户对部门的引用</li>
     * </ol>
     * 前三步在同一事务内，任一步失败都会整体回滚。第 4 步的事件在
     * 事务内发布，但订阅方用 {@code @ApplicationModuleListener} 消费，
     * 实际执行在事务提交之后。
     *
     * <p><b>并发说明：</b>“检查子部门”与“删除”之间存在竞态窗口——
     * 并发创建子部门时，可能在本方法检查通过之后、删除提交之前插入
     * 一条 {@code parentId} 指向本部门的记录，导致孤儿节点。当前实现
     * 未对此加锁；如需强一致，应在数据库层加 {@code parent_id} 外键，
     * 或对本部门行加 {@code SELECT ... FOR UPDATE}。
     */
    @Override
    @Transactional
    public void deleteDepartment(Long id) {
        getDepartment(id);
        // 子部门存在性校验：有子部门时拒绝删除
        if (departmentRepository.existsByParentId(id)) {
            throw new IllegalStateException("存在子部门，无法删除: " + id);
        }
        departmentRepository.deleteById(id);
        // 跨模块关联（iam_user.department_id）由 user 模块监听事件异步清理
        eventPublisher.publishEvent(new DepartmentDeletedEvent(id));
        log.info("删除部门成功, id={}", id);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 计算祖先链。
     *
     * <p><b>规则：</b>顶级部门为 {@link #ROOT_ANCESTORS}（{@code "0"}）；
     * 非顶级为「父部门 {@code ancestors} + "," + 父部门 id」。
     *
     * <p><b>空值防御：</b>父部门的 {@code ancestors} 为 {@code null} 或空白时
     * （历史数据、手工插入、迁移遗漏）按根处理，避免拼出 {@code "null,5"}
     * 这类脏数据——这类数据会让后续 {@code ancestors LIKE '0,5%'} 的
     * 子树查询完全失效，且难以追溯。
     *
     * <p><b>未来 move 操作的隐患：</b>本方法只在创建时调用，新部门还没有
     * id，不会把 {@code parentId} 指向自己。但未来实现 {@code moveDepartment}
     * 时，必须加校验“不能把节点移动到自己或自己的子孙下”——否则
     * {@code ancestors} 链会形成环，子树查询会死循环。
     *
     * @param parentId 父部门 ID；{@code null} 或 {@code 0} 表示顶级
     * @return 逗号分隔的祖先链
     * @throws IllegalArgumentException 父部门不存在时抛出
     */
    private String resolveAncestors(Long parentId) {
        // 顶级部门：直接返回 "0"
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return ROOT_ANCESTORS;
        }
        DepartmentDO parent = departmentRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("父级部门不存在: " + parentId));
        String parentAncestors = parent.getAncestors();
        // 父部门 ancestors 为空时按根处理，避免拼出 "null,5"
        if (parentAncestors == null || parentAncestors.isBlank()) {
            parentAncestors = ROOT_ANCESTORS;
        }
        return parentAncestors + "," + parent.getId();
    }

    /**
     * 校验部门名称在<b>同一父部门</b>下唯一。
     *
     * <p><b>与 DDL 对齐：</b>DDL 的唯一索引是
     * {@code uk_iam_department_tenant_parent_name}
     * {@code (tenant_id, parent_id, active_department_name)}——同一父部门下
     * 名称唯一，不同父部门下可以有同名部门。本方法按同样的口径校验，
     * 避免 Service 层比 DDL 更严格导致误拒合法请求。
     *
     * <p>{@code parentId} 为 {@code null} 时归一为 {@link #ROOT_PARENT_ID}，
     * 与 DDL 中顶级部门的 {@code parent_id = 0} 一致。
     *
     * <p>{@code departmentName} 为 {@code null} 或空白时跳过校验——由 DTO 层的
     * {@code @NotBlank} 保证非空，服务层只处理“重复”这一维度。
     *
     * <p><b>并发下前置校验不可靠：</b>两个并发请求可能同时通过本校验，
     * 最终依赖数据库的唯一约束兜底。冲突时抛 {@code DuplicateKeyException}，
     * 由全局异常处理器转为友好错误。
     *
     * @param parentId       父部门 ID；{@code null} 时按根处理
     * @param departmentName 待校验的部门名称
     * @param excludeId      排除的部门 ID；{@code null} 表示不排除（创建场景）
     * @throws IllegalArgumentException 同一父部门下名称已被占用时抛出
     */
    private void validateDepartmentNameUnique(Long parentId, String departmentName, Long excludeId) {
        if (departmentName == null || departmentName.isBlank()) {
            return;
        }
        Long effectiveParentId = parentId == null ? ROOT_PARENT_ID : parentId;
        boolean exists = (excludeId == null)
                ? departmentRepository.existsByParentIdAndDepartmentName(
                effectiveParentId, departmentName)
                : departmentRepository.existsByParentIdAndDepartmentNameAndIdNot(
                effectiveParentId, departmentName, excludeId);
        if (exists) {
            throw new IllegalArgumentException(
                    "同级部门下名称已存在: parentId=" + effectiveParentId
                            + ", name=" + departmentName);
        }
    }

    /**
     * 校验负责人用户存在。
     *
     * <p>{@code managerUserId} 为 {@code null} 时跳过——负责人是可选字段。
     * 非空时通过 {@link UserFacade#existsById(Long)} 校验用户存在且未删除。
     * 这是 {@code organization → user} 的同步依赖，方向单向：
     * {@code user} 模块不反向同步依赖本模块的内部类型。
     *
     * <p><b>悬空 managerUserId 的风险：</b>如果用户创建后被删除（逻辑删除），
     * 而部门仍引用它，{@code managerUserId} 会变成悬空引用。这是“不反向
     * 依赖”的代价——本模块不监听用户删除事件来清理 {@code managerUserId}。
     * 前端展示时应容忍查不到负责人用户的情况（如显示“负责人已删除”）。
     *
     * @param managerUserId 负责人用户 ID；{@code null} 时跳过校验
     * @throws IllegalArgumentException 用户不存在时抛出
     */
    private void validateManagerUser(Long managerUserId) {
        if (managerUserId == null) {
            return;
        }
        if (!userFacade.existsById(managerUserId)) {
            throw new IllegalArgumentException("负责人用户不存在: " + managerUserId);
        }
    }

    /**
     * 递归构造子树。
     *
     * <p><b>自底向上构造：</b>先递归得到子节点的 {@code DepartmentResponse}
     * 列表，再构造当前节点的 {@code DepartmentResponse} 并把子节点列表传入。
     * 这是因为 {@link DepartmentResponse} 是不可变 {@code record}，
     * 不能“先建父节点再往里塞子节点”。
     *
     * <p><b>排序说明：</b>{@code byParent} 来自 {@link #listAll()}（已排序），
     * 且用 {@code LinkedHashMap} 保序，所以本方法中的 {@code sorted} 是
     * 防御性冗余。保留它是为了未来 {@code listAll} 或分组逻辑变更时
     * 不破坏排序。节点数量小，排序开销可忽略。
     *
     * @param parentId 当前层的父节点 ID
     * @param byParent 按 {@code parentId} 分组的部门索引
     * @return 当前层的 {@code DepartmentResponse} 列表，按 {@code sortOrder} 升序
     */
    private List<DepartmentResponse> buildChildren(Long parentId,
                                                   Map<Long, List<DepartmentDO>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                // 防御性排序：输入已排序，保留此排序以防上游变更
                .sorted(Comparator.comparing(
                        DepartmentDO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(child -> DepartmentAdminConverter.toResponse(
                        child, buildChildren(child.getId(), byParent)))
                .toList();
    }
}
