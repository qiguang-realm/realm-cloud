package cn.realm.cloud.system.server.role.internal.persistence;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * 角色仓储接口。
 *
 * <p>方法签名使用业务语义，不暴露 MyBatis-Plus 的 {@code Wrapper}、{@code IPage}，
 * 使 {@code internal.application} 层与具体 ORM 解耦。
 *
 * <p>职责边界：
 * <ul>
 *   <li><b>本接口只负责持久化读写</b>，不承担业务规则：
 *       {@code roleCode} / {@code roleName} 的唯一性校验、状态值校验、
 *       {@code dataScope} 变更时的部门关联清理，均由应用服务在事务内编排</li>
 *   <li><b>所有查询（含 {@link #findById}、{@link #findByIds}）默认只返回
 *       未逻辑删除（{@code isDeleted = 0}）的记录</b>。{@code RoleDO} 未标注
 *       {@code @TableLogic}，因此实现必须显式追加 {@code is_deleted = 0} 条件，
 *       不能依赖 MyBatis-Plus 的自动逻辑删除</li>
 *   <li>方法对空入参（{@code null} / 空集合 / 空白字符串）返回空结果而非抛异常，
 *       调用方无需为每个调用点重复判空</li>
 * </ul>
 *
 * <p><b>参数校验职责：</b>本接口不校验 {@code pageSize} 的范围——
 * 校验由 {@code RoleServiceImpl.page} 保证（HTTP 入口由 {@code RolePageRequest}
 * 的 {@code @Min} / {@code @Max} 拦截，内部调用由 Service 层显式校验）。
 * 本接口信任入参已合法，实现直接委托 {@code BaseMapperX.selectPage}。
 *
 * <p><b>两个逻辑删除辅助列：</b>{@code activeRoleCode} 和 {@code activeRoleName}
 * 是 {@code role_code} / {@code role_name} 在“未删除角色中唯一”的实现手段。
 * 实现必须在 {@link #save} 的更新分支和 {@link #deleteById} 中<b>同时</b>
 * 显式维护这两个列——漏一个，对应的唯一约束就不会释放，新建同名/同编码
 * 角色会撞约束。这是 {@code RoleDO} 比 {@code MenuDO}（一个辅助列）
 * 更容易出错的地方。
 *
 * <p>实现由 {@code RoleRepositoryImpl} 提供，MyBatis-Plus 的 {@code RoleMapper}
 * 仅在该实现类内部使用。
 *
 * @author QI Guang
 */
public interface RoleRepository {

    // ==================== 查询 ====================

    /**
     * 按主键查询未删除的角色。
     *
     * @param id 角色 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除的角色；不存在或已逻辑删除时返回空
     */
    Optional<RoleDO> findById(Long id);

    /**
     * 按主键批量查询未删除的角色。
     *
     * @param ids 角色 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除的角色列表，顺序不保证与入参一致
     */
    List<RoleDO> findByIds(List<Long> ids);

    /**
     * 分页查询未删除的角色，按 {@code sortOrder}、{@code id} 升序排列。
     *
     * <p><b>参数校验：</b>本方法信任 {@code pageParam} 已合法——
     * {@code pageSize} 的取值范围由 {@code RoleServiceImpl.page} 保证。
     * 实现直接委托 {@code BaseMapperX.selectPage(PageParam, Wrapper)}，
     * 不做额外的范围检查。
     *
     * <p>过滤条件：
     * <ul>
     *   <li>{@code roleName} 模糊匹配；为 {@code null} 或空白时不过滤</li>
     *   <li>{@code status} 精确匹配；为 {@code null} 时不过滤</li>
     * </ul>
     *
     * @param pageParam 分页参数
     * @param roleName  角色名称模糊匹配；为 {@code null} 或空白时不过滤
     * @param status    状态精确匹配；为 {@code null} 时不过滤
     * @return 分页结果
     */
    PageResult<RoleDO> page(PageParam pageParam, String roleName, Integer status);

    // ==================== 存在性判断 ====================

    /**
     * 判断角色编码是否已被未删除的角色占用。
     *
     * @param roleCode 角色编码；为 {@code null} 或空白时返回 {@code false}
     */
    boolean existsByRoleCode(String roleCode);

    /**
     * 判断角色编码是否已被<b>除指定 ID 之外</b>的未删除角色占用。
     *
     * <p>供更新场景使用，排除自身，避免把未改动的当前值判为冲突。
     * 当前 {@code roleCode} 创建后不可修改，更新时不会触发冲突，
     * 但保留本方法以便未来放宽约束时复用。
     *
     * @param roleCode  角色编码；为 {@code null} 或空白时返回 {@code false}
     * @param excludeId 排除的角色 ID；为 {@code null} 时等价于
     *                  {@link #existsByRoleCode(String)}
     */
    boolean existsByRoleCodeAndIdNot(String roleCode, Long excludeId);

    /**
     * 判断角色名称是否已被未删除的角色占用。
     *
     * @param roleName 角色名称；为 {@code null} 或空白时返回 {@code false}
     */
    boolean existsByRoleName(String roleName);

    /**
     * 判断角色名称是否已被<b>除指定 ID 之外</b>的未删除角色占用。
     *
     * <p>供更新场景使用，排除自身，避免把未改动的当前值判为冲突。
     *
     * @param roleName  角色名称；为 {@code null} 或空白时返回 {@code false}
     * @param excludeId 排除的角色 ID；为 {@code null} 时等价于
     *                  {@link #existsByRoleName(String)}
     */
    boolean existsByRoleNameAndIdNot(String roleName, Long excludeId);

    // ==================== 写入 ====================

    /**
     * 保存角色：{@code id} 为 {@code null} 时插入，否则更新。
     *
     * <p>实现必须同步维护<b>两个</b>逻辑删除下的唯一约束辅助列：
     * <ul>
     *   <li>{@code activeRoleCode}：未删除时等于 {@code roleCode}，否则为 {@code null}</li>
     *   <li>{@code activeRoleName}：未删除时等于 {@code roleName}，否则为 {@code null}</li>
     * </ul>
     * <b>注意：</b>MyBatis-Plus 默认的 {@code updateById} 会忽略 {@code null} 字段，
     * 如果本次更新要把某个业务列置空，必须用 {@code UpdateWrapper} 显式
     * {@code set} 为 {@code null}，否则辅助列会保留旧值、唯一约束不释放。
     *
     * <p><b>更新分支必须显式带 {@code is_deleted = 0} 条件</b>——
     * 否则调用方传入一个已逻辑删除的 {@code id}，会把该记录“改活”。
     *
     * <p>本方法不负责 {@code roleCode} 不可修改的约束——那由
     * {@code RoleServiceImpl.updateRole} 用数据库原值覆盖完成。
     *
     * @param role 待保存的角色，不能为 {@code null}
     */
    void save(RoleDO role);

    /**
     * 逻辑删除角色。
     *
     * <p>实现必须将 {@code isDeleted} 置为 {@code 1}，<b>并显式将两个辅助列
     * {@code activeRoleCode} / {@code activeRoleName} 置为 {@code null}</b>
     * 以释放唯一约束。由于 {@code updateById} 忽略 {@code null} 字段，
     * 这里必须使用 {@code UpdateWrapper} 或 {@code LambdaUpdateWrapper} 显式 {@code set}。
     *
     * <p>本方法不做“是否允许删除”的业务判断，也不清理同模块的关联表
     * （{@code iam_role_menu}、{@code iam_role_department}）——这些由
     * {@code RoleServiceImpl.deleteRole} 在调用本方法前后编排。
     *
     * @param id 角色 ID；为 {@code null} 时不做任何操作
     */
    void deleteById(Long id);
}
