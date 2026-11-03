package cn.realm.cloud.system.server.organization.internal.application;

import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.DepartmentResponse;
import cn.realm.cloud.system.server.organization.internal.persistence.DepartmentDO;

import java.util.List;
import java.util.Optional;

/**
 * 部门模块应用服务。
 *
 * <p>编排部门的用例：创建、更新、树形查询、状态变更、删除。
 * 负责以下业务规则——这些规则不进入 {@code DepartmentRepository}，
 * 仓储只负责数据存取：
 * <ul>
 *   <li>{@code ancestors} 的拼接与子孙链重算</li>
 *   <li>删除前的子部门校验</li>
 *   <li>部门名称的<b>同级唯一</b>校验</li>
 *   <li>{@code managerUserId} 的存在性校验</li>
 * </ul>
 *
 * <p><b>部门名称唯一性：同级唯一。</b>
 * 与 DDL 的 {@code uk_iam_department_tenant_parent_name}
 * {@code (tenant_id, parent_id, active_department_name)} 保持一致——
 * 同一父部门下名称不重复，不同父部门下可以有同名部门
 * （如“华东区/研发部”与“华南区/研发部”并存）。
 * 如果业务确实要求“全公司部门名称唯一”，需要同时修改本类的校验逻辑
 * 和 DDL 的唯一索引——两边口径必须一致，不能一边同级一边全局。
 *
 * <p><b>模块可见性：</b>本接口位于 {@code internal} 包，<b>不暴露给其他模块</b>。
 * 其他模块需要部门能力（如 {@code user} 模块校验用户的 {@code departmentId}、
 * {@code role} 模块校验自定义数据范围）时，通过模块根包的
 * {@code OrganizationFacade} 访问，而不是注入本接口。
 * Spring Modulith 的 {@code verify()} 会阻止跨模块引用 {@code internal} 类型。
 *
 * <p><b>删除语义（与菜单模块一致）：</b>{@link #deleteDepartment(Long)}
 * 只校验是否存在未删除的子部门，<b>不</b>查询 {@code iam_user} 表校验
 * 是否被用户引用——{@code iam_user.department_id} 归 {@code user} 模块所有，
 * 本模块查询它会形成反向数据依赖。用户侧的引用由 {@code user} 模块监听
 * {@code DepartmentDeletedEvent} 异步清理，最终一致。在清理完成之前，
 * {@code user.department_id} 可能短暂指向已删除部门——订阅方查询时
 * 应容忍查不到对应部门的情况，清理逻辑须幂等。
 *
 * <p><b>跨模块依赖方向：</b>本模块通过 {@code UserFacade} <b>同步</b>校验
 * {@code managerUserId} 的有效性（{@code organization → user}）。
 * 这要求 {@code organization/package-info.java} 的 {@code allowedDependencies}
 * 包含 {@code "user"}。{@code user} 模块对部门的使用应通过
 * {@code OrganizationFacade} 或事件完成，<b>不反向</b>同步依赖本模块的
 * {@code internal} 类型——否则会形成 {@code organization ↔ user} 循环，
 * {@code ModularityTest.verify()} 会失败。
 *
 * <p><b>分层说明：</b>{@link #listTree()} 返回 {@link DepartmentResponse}，
 * 使本接口依赖 {@code internal.api.web.admin.dto} 中的类型。这在
 * {@code internal} 包内部不构成模块边界违规，属于“树组装放在 Service
 * 比放在 Controller 更合适”的权衡——与 {@code MenuService.listTree}
 * 保持同一套做法。如果未来希望 Service 完全不依赖入站 DTO，
 * 可引入中性的 {@code DepartmentTreeNode}，由 Converter 映射为
 * {@code DepartmentResponse}。
 *
 * @author QI Guang
 */
public interface DepartmentService {

    // ==================== 查询 ====================

    /**
     * 按 ID 查询部门，不存在时抛出业务异常。
     *
     * <p>供 Controller 使用——“不存在”应返回 404，而不是静默返回空。
     * 供 Facade 层做存在性判断时，应使用不抛异常的
     * {@link #findById(Long)}。
     *
     * @param id 部门 ID
     * @return 部门持久化对象
     * @throws IllegalArgumentException 部门不存在或已逻辑删除时抛出
     */
    DepartmentDO getDepartment(Long id);

    /**
     * 按 ID 查询部门，不存在时返回空。
     *
     * <p>与 {@link #getDepartment(Long)} 的区别：本方法不抛异常，
     * 供“存在性不影响主流程”的场景使用——如 {@code OrganizationFacade.existsById}
     * 的实现、可选的父部门查找、跨模块的存在性校验。
     *
     * @param id 部门 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除的部门；不存在或已逻辑删除时返回空
     */
    Optional<DepartmentDO> findById(Long id);

    /**
     * 按主键批量查询部门。
     *
     * <p>供 {@code OrganizationFacade.existsByIds} / {@code findByIds}
     * 使用——其他模块批量校验部门 ID 有效性、批量查询部门信息。
     * 返回顺序不保证与入参一致，需要顺序的调用方自行按入参重排。
     *
     * @param ids 部门 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除的部门列表，顺序不保证与入参一致
     */
    List<DepartmentDO> listByIds(List<Long> ids);

    /**
     * 查询全部未删除部门（扁平列表，不含树结构）。
     *
     * <p>返回结果按 {@code sortOrder} 升序排列，{@code null} 排末尾。
     * 排序在 Service 层做防御性重排——MySQL 的 {@code ORDER BY}
     * 对 {@code NULL} 的处理是“排最前”，与业务期望的“排末尾”相反，
     * Service 层用 {@code nullsLast} 修正这个差异。
     *
     * <p>树形展示请使用 {@link #listTree()}。
     *
     * @return 全部部门的扁平列表；无数据时返回空列表
     */
    List<DepartmentDO> listAll();

    /**
     * 查询指定父节点下的直接子部门（未删除）。
     *
     * <p>返回结果按 {@code sortOrder} 升序排列，{@code null} 排末尾。
     * 用于逐层展开或局部树查询；完整树请使用 {@link #listTree()}。
     *
     * @param parentId 父部门 ID；顶级部门的子部门其 {@code parentId} 为 {@code 0}；
     *                 为 {@code null} 时返回空列表
     * @return 直接子部门列表
     */
    List<DepartmentDO> listByParentId(Long parentId);

    /**
     * 查询完整部门树（已组装 {@code children}）。
     *
     * <p>树组装在服务层完成：
     * <ul>
     *   <li>按 {@code parentId} 建立父子关系；{@code parentId} 为
     *       {@code null} 或 {@code 0} 的节点作为根节点</li>
     *   <li>每一层按 {@code sortOrder} 升序排列，{@code null} 排末尾</li>
     *   <li>找不到父节点的孤儿节点被丢弃——这通常意味着数据不一致
     *       （父部门被物理删除、历史数据缺失等），丢弃而不是挂到根上，
     *       避免掩盖问题</li>
     * </ul>
     *
     * <p>返回的 {@link DepartmentResponse} 是不可变的：子节点列表在构造时
     * 由紧凑构造器 {@code List.copyOf} 复制，调用方无法通过修改返回的树
     * 影响后续查询。
     *
     * @return 已组装的部门树；无数据时返回空列表
     */
    List<DepartmentResponse> listTree();

    // ==================== 命令 ====================

    /**
     * 创建部门。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验 {@code departmentName} 在<b>同一父部门</b>下唯一</li>
     *   <li>校验 {@code managerUserId} 非空时对应用户存在</li>
     *   <li>根据 {@code parentId} 计算 {@code ancestors}：顶级部门为
     *       {@code "0"}，否则为「父部门 {@code ancestors} + "," + 父部门 id」</li>
     * </ul>
     *
     * <p>{@code ancestors} 由服务端计算，调用方不应在传入的
     * {@link DepartmentDO} 中设置该字段，即使设置了也会被覆盖。
     *
     * <p><b>并发下的唯一性：</b>前置校验在并发下可能两个请求同时通过
     * （如两个请求同时创建“研发部”到同一父部门下），最终依赖数据库的
     * {@code uk_iam_department_tenant_parent_name} 唯一约束兜底。
     * 冲突时抛 {@code DuplicateKeyException}，由全局异常处理器转为
     * 友好的错误信息。
     *
     * @param department 待创建的部门，{@code ancestors} 由服务端填充
     * @return 新部门的 ID
     * @throws IllegalArgumentException 同级名称重复、负责人用户不存在、
     *                                  父部门不存在时抛出
     */
    Long createDepartment(DepartmentDO department);

    /**
     * 更新部门。
     *
     * <p>不修改 {@code parentId} 和 {@code ancestors}：移动节点会触发
     * 整棵子树的 {@code ancestors} 重算，由独立的 move 用例处理，
     * 避免与普通编辑混在一起。即使调用方在 {@code department} 中传入了
     * 不同的 {@code parentId}，也会被数据库中的原值覆盖。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验部门存在</li>
     *   <li>校验 {@code departmentName} 在同一父部门下唯一，<b>排除自身</b></li>
     *   <li>校验 {@code managerUserId} 非空时对应用户存在</li>
     * </ul>
     *
     * @param department 待更新的部门，必须包含有效 {@code id}
     * @throws IllegalArgumentException 部门不存在、同级名称与其他部门冲突、
     *                                  负责人用户不存在时抛出
     */
    void updateDepartment(DepartmentDO department);

    /**
     * 变更部门状态（启用 / 停用）。
     *
     * <p>状态变更后发布 {@code DepartmentStatusChangedEvent}，供其他模块
     * （如缓存刷新、审计）响应。状态未实际变化时不发布事件、不写库——
     * 避免订阅方收到“状态从 1 变为 1”这类无意义的通知。
     *
     * @param id     部门 ID
     * @param status 新状态：0-正常、1-停用
     * @throws IllegalArgumentException 部门不存在或状态值非法时抛出
     */
    void changeStatus(Long id, Integer status);

    /**
     * 删除部门。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验部门存在</li>
     *   <li>校验不存在未删除的子部门——有子部门时拒绝删除，
     *       避免留下孤儿节点和断裂的 {@code ancestors} 链</li>
     * </ul>
     *
     * <p><b>用户引用的处理：</b>本方法<b>不</b>同步校验部门是否被用户引用。
     * {@code iam_user.department_id} 归 {@code user} 模块所有，本模块查询
     * 它会形成反向数据依赖。用户侧的引用由 {@code user} 模块监听
     * {@code DepartmentDeletedEvent} 异步清理，最终一致。在清理完成之前，
     * {@code user.department_id} 可能短暂指向已删除部门——订阅方查询时
     * 应容忍查不到对应部门的情况，清理逻辑须幂等。
     *
     * <p><b>并发说明：</b>“检查子部门”与“删除”之间存在竞态窗口——
     * 并发创建子部门时，可能在本方法检查通过之后、删除提交之前插入
     * 一条 {@code parentId} 指向本部门的记录，导致孤儿节点。当前实现
     * 未对此加锁；如需强一致，应在数据库层加 {@code parent_id} 外键，
     * 或对本部门行加 {@code SELECT ... FOR UPDATE}。
     *
     * @param id 部门 ID
     * @throws IllegalArgumentException 部门不存在时抛出
     * @throws IllegalStateException    存在子部门时抛出
     */
    void deleteDepartment(Long id);
}
