package cn.realm.cloud.system.server.role.internal.application;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.role.internal.persistence.RoleDO;

import java.util.List;
import java.util.Optional;

/**
 * 角色模块应用服务。
 *
 * <p>编排角色的用例：创建、更新、分页查询、状态变更、删除。
 * 负责角色编码/名称的唯一性校验、{@code dataScope} 变更时的部门关联清理、
 * 删除时同模块关联表（{@code iam_role_menu}、{@code iam_role_department}）的
 * 清理——这些规则不进入 {@code RoleRepository}，仓储只负责数据存取。
 *
 * <p><b>模块可见性：</b>本接口位于 {@code internal} 包，<b>不暴露给其他模块</b>。
 * 其他模块需要角色能力（如 {@code user} 模块校验角色存在性）时，通过模块根包
 * 的 {@code RoleFacade} 访问，而不是注入本接口。Spring Modulith 的
 * {@code verify()} 会阻止跨模块引用 {@code internal} 类型。
 *
 * <p><b>校验职责：</b>本接口是“分页参数合法性”的最终校验点。
 * HTTP 入口由 {@code RolePageRequest} 的 {@code @Min} / {@code @Max}
 * 拦截，内部调用（定时任务、其他 Service）绕过了 Controller，由实现类
 * {@link #page(PageParam, String, Integer)} 显式校验。Repository / Mapper /
 * 工具类都不做校验——它们信任 Service 层传入的合法值。这与
 * {@code UserService} / {@code PositionService} 的分层原则一致。
 *
 * <p><b>删除语义：</b>{@link #deleteRole(Long)} 清理同模块的两个关联表
 * （{@code iam_role_menu}、{@code iam_role_department}），然后逻辑删除角色
 * 并发布 {@code RoleDeletedEvent}。<b>不</b>查询 {@code iam_user_role}——
 * 该表归 {@code user} 模块所有，本模块查询它会形成反向数据依赖。用户侧的
 * 关联由 {@code user} 模块监听事件异步清理，最终一致。
 *
 * <p><b>关于 {@code roleCode}：</b>它是租户内唯一的业务标识，<b>创建后
 * 不可修改</b>。更新方法会用数据库中的原值覆盖传入值，即使调用方误传
 * 也不会生效。
 *
 * @author QI Guang
 */
public interface RoleService {

    // ==================== 查询 ====================

    /**
     * 按 ID 查询角色，不存在时抛出业务异常。
     *
     * @param id 角色 ID
     * @return 角色持久化对象
     * @throws IllegalArgumentException 角色不存在或已逻辑删除时抛出
     */
    RoleDO getRole(Long id);

    /**
     * 按 ID 查询角色，不存在时返回空。
     *
     * <p>与 {@link #getRole(Long)} 的区别：本方法不抛异常，
     * 供“存在性不影响主流程”的场景使用。
     *
     * @param id 角色 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除的角色；不存在或已逻辑删除时返回空
     */
    Optional<RoleDO> findById(Long id);

    /**
     * 按主键批量查询角色。
     *
     * <p>供跨模块的批量校验使用，如 {@code user} 模块在分配用户角色前
     * 通过 {@code RoleFacade.existsByIds(ids)} 校验角色 ID 有效性。
     *
     * <p>入参数量上限由调用方控制——角色的数量通常比用户少一个数量级，
     * 单个用户持有的角色数量也不会太多，本方法不设置批量上限。
     * 如果未来出现“批量导入角色”等大数量场景，再考虑加入上限校验。
     *
     * @param ids 角色 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除的角色列表，顺序不保证与入参一致
     */
    List<RoleDO> listByIds(List<Long> ids);

    /**
     * 分页查询角色，按 {@code sortOrder}、{@code id} 升序排列。
     *
     * <p><b>参数校验：</b>实现类会校验 {@code pageSize} 的范围
     * （{@code 1 ~ PageParam.MAX_PAGE_SIZE}），超出时抛
     * {@link IllegalArgumentException}。调用方不应假设本方法会接受
     * 任意 {@code pageSize}。
     *
     * <p>分页与排序完全委托给仓储层——角色是平铺列表，不需要 Service
     * 层做额外的结构组装。仓储层应在 SQL 中按 {@code sort_order}、
     * {@code id} 升序排列。
     *
     * @param pageParam 分页参数
     * @param roleName  角色名称模糊匹配；为 {@code null} 或空白时不过滤
     * @param status    状态精确匹配；为 {@code null} 时不过滤
     * @return 分页结果
     */
    PageResult<RoleDO> page(PageParam pageParam, String roleName, Integer status);

    // ==================== 命令 ====================

    /**
     * 创建角色。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验 {@code roleCode} 在未删除角色中唯一</li>
     *   <li>校验 {@code roleName} 在未删除角色中唯一</li>
     * </ul>
     *
     * <p>如果 {@code dataScope = 2}（自定义），部门范围不通过本方法设置——
     * 创建成功后由 {@code RoleDepartmentService.replaceRoleDepartments}
     * 单独设置。这是为了保持“创建角色”和“设置部门范围”两个用例的边界，
     * 避免创建请求体耦合部门列表。
     *
     * @param role 待创建的角色
     * @return 新角色的 ID
     * @throws IllegalArgumentException 编码或名称重复时抛出
     */
    Long createRole(RoleDO role);

    /**
     * 更新角色。
     *
     * <p>不修改 {@code roleCode}：它是业务标识，创建后不可修改，
     * 被 {@code iam_user_role} 等关联表引用时若变更会导致追溯困难。
     * 即使调用方在 {@code role} 中传入了不同的 {@code roleCode}，
     * 也会被数据库中的原值覆盖。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验角色存在</li>
     *   <li>校验 {@code roleName} 唯一性，<b>排除自身</b></li>
     *   <li>{@code dataScope} 从 {@code 2}（自定义）变为其他值时，
     *       清理 {@code iam_role_department} 中的残留关联——
     *       非自定义范围下部门列表无意义</li>
     * </ul>
     *
     * @param role 待更新的角色，必须包含有效 {@code id}
     * @throws IllegalArgumentException 角色不存在、名称与其他角色冲突时抛出
     */
    void updateRole(RoleDO role);

    /**
     * 变更角色状态（启用 / 停用）。
     *
     * <p>状态变更后发布 {@code RoleStatusChangedEvent}，供其他模块
     * （如认证/权限模块刷新缓存、审计）响应。状态未实际变化时
     * 不发布事件、不写库。
     *
     * @param id     角色 ID
     * @param status 新状态：0-正常、1-停用
     * @throws IllegalArgumentException 角色不存在或状态值非法时抛出
     */
    void changeStatus(Long id, Integer status);

    /**
     * 删除角色。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验角色存在</li>
     *   <li>清理同模块关联表 {@code iam_role_menu}——菜单授权随角色删除
     *       而失效，这是 role 模块持有的表，直接清理</li>
     *   <li>清理同模块关联表 {@code iam_role_department}——自定义数据范围
     *       随角色删除而失效，同样由 role 模块直接清理</li>
     *   <li>逻辑删除角色，发布 {@code RoleDeletedEvent}</li>
     * </ul>
     *
     * <p><b>用户角色关联的处理：</b>本方法<b>不</b>清理 {@code iam_user_role}。
     * 该表归 {@code user} 模块所有，本模块查询或写入它会形成反向数据依赖。
     * {@code user} 模块监听 {@code RoleDeletedEvent} 异步清理，最终一致。
     * 在清理完成之前，{@code iam_user_role} 中可能短暂存在指向已删除角色的
     * 记录——订阅方查询时应容忍查不到对应角色的情况，清理逻辑须幂等。
     *
     * @param id 角色 ID
     * @throws IllegalArgumentException 角色不存在时抛出
     */
    void deleteRole(Long id);
}
