package cn.realm.cloud.system.server.organization.internal.persistence;

import cn.realm.cloud.system.server.organization.internal.persistence.mapper.DepartmentMapper;

import java.util.List;
import java.util.Optional;

/**
 * 部门仓储接口。
 *
 * <p>方法签名使用业务语义，不暴露 MyBatis-Plus 的 {@code Wrapper}、{@code IPage}，
 * 使 {@code internal.application} 层与具体 ORM 解耦。
 *
 * <p><b>职责边界：</b>
 * <ul>
 *   <li><b>本接口只负责持久化读写</b>，不承担业务规则：
 *       {@code ancestors} 的拼接、移动节点时子孙链的重算、删除前是否有子部门、
 *       部门名称唯一性、{@code managerUserId} 的存在性校验，均由应用服务在事务内编排</li>
 *   <li><b>所有查询（含 {@link #findById}、{@link #findByIds}）默认只返回
 *       未逻辑删除（{@code isDeleted = 0}）的记录</b>。{@code DepartmentDO} 未标注
 *       {@code @TableLogic}，因此实现必须显式追加 {@code is_deleted = 0} 条件，
 *       不能依赖 MyBatis-Plus 的自动逻辑删除</li>
 *   <li>方法对空入参（{@code null} / 空集合 / 空白字符串）返回空结果而非抛异常，
 *       调用方无需为每个调用点重复判空</li>
 * </ul>
 *
 * <p><b>部门名称唯一性的两个维度：</b>本接口同时提供两种校验方法，
 * 对应不同的业务口径，调用方必须明确选哪一种：
 * <ul>
 *   <li><b>同级唯一（推荐，与 DDL 对齐）：</b>
 *       {@link #existsByParentIdAndDepartmentName(Long, String)} /
 *       {@link #existsByParentIdAndDepartmentNameAndIdNot(Long, String, Long)}。
 *       DDL 的唯一索引 {@code uk_iam_department_tenant_parent_name}
 *       {@code (tenant_id, parent_id, active_department_name)} 就是这一口径——
 *       同一父部门下名称唯一，不同父部门下可以同名
 *       （如“华东区/研发部”与“华南区/研发部”并存）。
 *       {@code DepartmentServiceImpl} 使用的就是这一组。</li>
 *   <li><b>全局唯一（保留，供特定场景使用）：</b>
 *       {@link #existsByDepartmentName(String)} /
 *       {@link #existsByDepartmentNameAndIdNot(String, Long)}。
 *       不区分 {@code parentId}，只要名称被任何一个未删除部门占用就返回 {@code true}。
 *       当前 DDL 并不保证全局唯一，因此这组方法只适合“需要在应用层额外约束
 *       全局唯一”的场景（比如某些租户要求全公司部门不重名）。
 *       如果业务没有这种特殊要求，<b>不要</b>使用这组方法——它会误拒
 *       “不同父部门下同名”的合法请求。</li>
 * </ul>
 *
 * <p>实现由 {@code DepartmentRepositoryImpl} 提供，MyBatis-Plus 的
 * {@link DepartmentMapper} 仅在该实现类内部使用。
 *
 * @author QI Guang
 */
public interface DepartmentRepository {

    // ==================== 查询 ====================

    /**
     * 按主键查询未删除的部门。
     *
     * @param id 部门 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除的部门；不存在或已逻辑删除时返回空
     */
    Optional<DepartmentDO> findById(Long id);

    /**
     * 按主键批量查询未删除的部门。
     *
     * @param ids 部门 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除的部门列表，顺序不保证与入参一致
     */
    List<DepartmentDO> findByIds(List<Long> ids);

    /**
     * 查询全部未删除部门，按 {@code sortOrder}、{@code id} 升序排列。
     *
     * <p>用于构建完整部门树；数据量通常有限（部门是低频变更的配置数据），
     * 不做分页。若未来部门量级增长到需要分页，应新增独立的分页方法，
     * 而不是修改本方法的语义。
     *
     * <p><b>排序注意：</b>SQL 的 {@code ORDER BY sort_order} 在 MySQL 下
     * 把 {@code NULL} 排在最前，与业务期望的“排末尾”相反。
     * {@code DepartmentServiceImpl.listAll()} 会在 Service 层用
     * {@code Comparator.nullsLast} 重排修正这个差异——本方法只保证
     * “有确定顺序”，不保证 {@code NULL} 的位置符合业务期望。
     */
    List<DepartmentDO> findAll();

    /**
     * 查询指定父节点下的直接子部门（未删除），按 {@code sortOrder}、
     * {@code id} 升序排列。
     *
     * @param parentId 父部门 ID；顶级部门的子部门其 {@code parentId} 为 {@code 0}；
     *                 为 {@code null} 时返回空列表
     */
    List<DepartmentDO> findByParentId(Long parentId);

    /**
     * 查询祖先链以给定前缀开头的所有子孙节点（未删除）。
     *
     * <p>部门的 {@code ancestors} 是逗号分隔的祖先 ID 列表，如节点 {@code 5}
     * 的子孙其 {@code ancestors} 以 {@code "0,1,5"} 开头。移动节点时需要
     * 找出全部子孙并重算它们的祖先链，本方法提供该集合。
     *
     * <p><b>实现必须以 {@code prefix + ","} 作为 LIKE 的前缀</b>，避免
     * {@code "0,1,5"} 误匹配 {@code "0,1,50"} 这类节点。补逗号后
     * {@code LIKE '0,1,5,%'} 只匹配以 {@code "0,1,5,"} 开头的链，
     * 同时自然排除了节点 5 自身（其 {@code ancestors} 是 {@code "0,1"}）。
     *
     * @param ancestorsPrefix 祖先链前缀，如 {@code "0,1,5"}；
     *                        为 {@code null} 或空白时返回空列表
     */
    List<DepartmentDO> findByAncestorsPrefix(String ancestorsPrefix);

    // ==================== 存在性判断：同级唯一（推荐） ====================

    /**
     * 判断<b>同一父部门下</b>是否已存在指定名称的未删除部门。
     *
     * <p><b>与 DDL 对齐：</b>DDL 的唯一索引
     * {@code uk_iam_department_tenant_parent_name}
     * {@code (tenant_id, parent_id, active_department_name)} 保证的就是
     * “同一父部门下名称唯一”。本方法与之一一对应，是部门名称唯一性的
     * <b>正确口径</b>——{@code DepartmentServiceImpl.createDepartment}
     * 使用的就是本方法。
     *
     * <p>不同父部门下可以有同名部门（如“华东区/研发部”与“华南区/研发部”），
     * 这是合理的数据模型。如果误用全局校验方法（{@link #existsByDepartmentName}），
     * 会拒绝这类合法请求。
     *
     * <p><b>并发下前置校验不可靠：</b>两个并发请求可能同时通过本校验，
     * 最终依赖数据库的唯一约束兜底。冲突时抛 {@code DuplicateKeyException}，
     * 由全局异常处理器转为友好错误。
     *
     * @param parentId       父部门 ID；顶级部门的子部门其 {@code parentId} 为 {@code 0}；
     *                       为 {@code null} 时返回 {@code false}
     * @param departmentName 部门名称；为 {@code null} 或空白时返回 {@code false}
     * @return 同一父部门下存在同名未删除部门时为 {@code true}
     */
    boolean existsByParentIdAndDepartmentName(Long parentId, String departmentName);

    /**
     * 判断<b>同一父部门下</b>是否已存在指定名称的未删除部门，<b>排除指定 ID</b>。
     *
     * <p>供更新场景使用——排除自身，避免把未改动的当前名称判为冲突。
     * {@code DepartmentServiceImpl.updateDepartment} 使用本方法时，
     * {@code parentId} 传的是数据库中的原值（部门不通过普通编辑移动），
     * 因此校验的是“同一父部门下是否有其他部门占用了这个名称”。
     *
     * @param parentId       父部门 ID；为 {@code null} 时返回 {@code false}
     * @param departmentName 部门名称；为 {@code null} 或空白时返回 {@code false}
     * @param excludeId      排除的部门 ID；为 {@code null} 时等价于
     *                       {@link #existsByParentIdAndDepartmentName(Long, String)}
     * @return 存在同名的其他未删除部门时为 {@code true}
     */
    boolean existsByParentIdAndDepartmentNameAndIdNot(
            Long parentId, String departmentName, Long excludeId);

    // ==================== 存在性判断：全局唯一（保留，特定场景使用） ====================

    /**
     * 判断部门名称是否已被<b>任意未删除部门</b>占用（不区分父部门）。
     *
     * <p><b>⚠️ 与 DDL 不一致：</b>DDL 只保证“同一父部门下唯一”，
     * 不保证全局唯一。本方法比 DDL 更严格——如果用它做前置校验，
     * 会拒绝“不同父部门下同名”的合法请求。
     *
     * <p><b>使用场景：</b>仅当业务明确要求“全公司部门名称唯一”
     * （如某些租户要求所有部门名称不重名）时使用。如果使用本方法，
     * 需要同步修改 DDL——把唯一索引从
     * {@code (tenant_id, parent_id, active_department_name)}
     * 改为 {@code (tenant_id, active_department_name)}，
     * 否则并发下的全局重名无法被数据库约束拦截。
     *
     * <p><b>当前部门模块不使用本方法</b>——{@code DepartmentServiceImpl}
     * 统一使用同级唯一的 {@link #existsByParentIdAndDepartmentName(Long, String)}。
     * 保留本方法是为了给“需要额外全局约束”的场景留一个入口，
     * 而不是让调用方误以为这是默认的校验口径。
     *
     * @param departmentName 部门名称；为 {@code null} 或空白时返回 {@code false}
     */
    boolean existsByDepartmentName(String departmentName);

    /**
     * 判断部门名称是否已被<b>除指定 ID 之外</b>的任意未删除部门占用
     * （不区分父部门）。
     *
     * <p>与 {@link #existsByDepartmentName(String)} 是同一口径
     * （全局唯一）的更新场景版本。使用限制与前者一致——
     * 只在业务明确要求“全公司部门名称唯一”时使用，且需同步修改 DDL。
     *
     * @param departmentName 部门名称；为 {@code null} 或空白时返回 {@code false}
     * @param excludeId      排除的部门 ID；为 {@code null} 时等价于
     *                       {@link #existsByDepartmentName(String)}
     */
    boolean existsByDepartmentNameAndIdNot(String departmentName, Long excludeId);

    // ==================== 存在性判断：子节点 ====================

    /**
     * 判断指定父节点下是否存在未删除的子部门。
     *
     * <p>删除部门前的守卫：有子部门时应拒绝删除，避免留下孤儿节点
     * 和断裂的 {@code ancestors} 链。
     *
     * <p><b>并发说明：</b>“检查子部门”与“删除”之间存在竞态窗口——
     * 并发创建子部门时可能在检查通过后、删除提交前插入一条记录，
     * 导致孤儿节点。当前实现未对此加锁；如需强一致，应在数据库层
     * 加 {@code parent_id} 外键，或对部门行加 {@code SELECT ... FOR UPDATE}。
     *
     * @param parentId 父部门 ID；为 {@code null} 时返回 {@code false}
     */
    boolean existsByParentId(Long parentId);

    // ==================== 写入 ====================

    /**
     * 保存部门：{@code id} 为 {@code null} 时插入，否则更新。
     *
     * <p>实现必须同步维护逻辑删除下的唯一约束辅助列
     * {@code activeDepartmentName}：未删除时等于 {@code departmentName}，
     * 否则为 {@code null}。<b>注意：</b>MyBatis-Plus 默认的 {@code updateById}
     * 会忽略 {@code null} 字段，如果本次更新要把 {@code departmentName}
     * 置空（实际不会发生，但接口语义上应支持），必须用 {@code UpdateWrapper}
     * 显式 {@code set} 为 {@code null}，否则辅助列会保留旧值、唯一约束不释放。
     *
     * <p><b>更新分支必须显式带 {@code is_deleted = 0} 条件</b>——
     * 否则调用方传入一个已逻辑删除的 {@code id}，会把该记录“改活”。
     *
     * <p><b>更新分支的字段范围：</b>只更新“部门资料”相关字段，
     * <b>不更新</b>以下字段：
     * <ul>
     *   <li>{@code parentId} / {@code ancestors}——移动节点由独立的 move 用例处理，
     *       不复用普通编辑。{@code DepartmentServiceImpl.updateDepartment}
     *       已用数据库原值覆盖这两个字段，Repository 层不 set 是第二重保护</li>
     *   <li>{@code tenantId}——部门不能跨租户迁移</li>
     *   <li>{@code createBy} / {@code createTime}——审计字段，创建后不变</li>
     *   <li>{@code version}——乐观锁版本号，当前未启用乐观锁</li>
     * </ul>
     *
     * <p><b>本方法不负责 {@code ancestors} 的计算</b>——调用方必须在调用前
     * 根据父节点设置好 {@code ancestors}（创建时由
     * {@code DepartmentServiceImpl.resolveAncestors} 计算），否则插入的
     * 节点祖先链为空，后续 {@link #findByAncestorsPrefix} 无法定位到它。
     *
     * @param department 待保存的部门，不能为 {@code null}
     */
    void save(DepartmentDO department);

    /**
     * 批量更新子孙节点的祖先链。
     *
     * <p>用于移动节点后重算整棵子树的 {@code ancestors}。实现应只更新
     * {@code ancestors} 字段，不整行回写，避免覆盖并发修改的其他字段。
     *
     * <p><b>当前实现为逐条更新</b>，适合单次移动影响的子孙数量较小的场景
     * （部门树通常深度有限、单节点子孙数不大）。如果未来出现“移动一个
     * 有上千子孙的顶级部门”的需求，应改为 {@code CASE WHEN} 批量更新或
     * 分批 {@code IN} 更新。
     *
     * @param departments 待更新的节点列表，每个节点需携带 {@code id} 和新的
     *                    {@code ancestors}；为 {@code null} 或空集合时不做任何操作
     */
    void updateAncestorsBatch(List<DepartmentDO> departments);

    /**
     * 逻辑删除部门。
     *
     * <p>实现必须将 {@code isDeleted} 置为 {@code 1}，<b>并显式将辅助列
     * {@code activeDepartmentName} 置为 {@code null}</b> 以释放唯一约束。
     * 由于 {@code updateById} 忽略 {@code null} 字段，这里必须使用
     * {@code UpdateWrapper} 或 {@code LambdaUpdateWrapper} 显式 {@code set}。
     *
     * <p>本方法不做“是否允许删除”的业务判断——子部门存在性等校验
     * 由应用服务在调用前完成。也不清理跨模块关联（{@code iam_user.department_id}
     * 归 {@code user} 模块所有），跨模块清理由 {@code DepartmentDeletedEvent}
     * 的订阅方异步完成。
     *
     * @param id 部门 ID；为 {@code null} 时不做任何操作
     */
    void deleteById(Long id);
}
