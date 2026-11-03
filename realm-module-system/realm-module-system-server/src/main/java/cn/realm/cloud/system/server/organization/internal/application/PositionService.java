package cn.realm.cloud.system.server.organization.internal.application;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.organization.internal.persistence.PositionDO;

import java.util.List;
import java.util.Optional;

/**
 * 岗位模块应用服务。
 *
 * <p>编排岗位的用例：创建、更新、分页查询、状态变更、删除。
 * 负责岗位编码/名称的唯一性校验、状态值校验，这些规则不进入
 * {@code PositionRepository}，仓储只负责数据存取。
 *
 * <p><b>模块可见性：</b>本接口位于 {@code internal} 包，<b>不暴露给其他模块</b>。
 * 其他模块需要岗位能力（如 {@code user} 模块校验岗位存在性、查询岗位信息）
 * 时，通过模块根包的 {@code OrganizationFacade} 访问，而不是注入本接口。
 * Spring Modulith 的 {@code verify()} 会阻止跨模块引用 {@code internal} 类型。
 *
 * <p><b>校验职责：</b>本接口是“分页参数合法性”的最终校验点。
 * HTTP 入口由 {@code PositionPageRequest} 的 {@code @Min} / {@code @Max}
 * 拦截，内部调用（定时任务、其他 Service）绕过了 Controller，由实现类
 * {@link #page(PageParam, String, Integer)} 显式校验。Repository / Mapper /
 * 工具类都不做校验——它们信任 Service 层传入的合法值。这与
 * {@code UserService} / {@code RoleService} 的分层原则一致。
 *
 * <p><b>删除语义（与部门、菜单模块一致）：</b>{@link #deletePosition(Long)}
 * 只校验岗位自身存在，<b>不</b>查询 {@code iam_user_position} 表校验是否被
 * 用户引用——该表归 {@code user} 模块所有，本模块查询它会形成反向数据依赖。
 * 用户侧的关联由 {@code user} 模块监听 {@code PositionDeletedEvent} 异步清理，
 * 最终一致。在清理完成之前，{@code iam_user_position} 中可能存在指向已删除
 * 岗位的记录——订阅方查询时应容忍查不到对应岗位的情况，清理逻辑须幂等。
 *
 * <p><b>关于 {@code positionCode}：</b>它是租户内唯一的业务标识，<b>创建后
 * 不可修改</b>。更新请求不包含该字段，服务层会用数据库中的原值覆盖，
 * 避免调用方误传导致标识变更、追溯困难。
 *
 * @author QI Guang
 */
public interface PositionService {

    // ==================== 查询 ====================

    /**
     * 按 ID 查询岗位，不存在时抛出业务异常。
     *
     * <p>供 Controller 使用——“不存在”应返回 404，而不是静默返回空。
     *
     * @param id 岗位 ID
     * @return 岗位持久化对象
     * @throws IllegalArgumentException 岗位不存在或已逻辑删除时抛出
     */
    PositionDO getPosition(Long id);

    /**
     * 按 ID 查询岗位，不存在时返回空。
     *
     * <p>与 {@link #getPosition(Long)} 的区别：本方法不抛异常，
     * 供“存在性不影响主流程”的场景使用——如 {@code OrganizationFacade.existsById}
     * 的实现、跨模块的存在性校验。
     *
     * @param id 岗位 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除的岗位；不存在或已逻辑删除时返回空
     */
    Optional<PositionDO> findById(Long id);

    /**
     * 按主键批量查询岗位。
     *
     * <p>供跨模块的批量校验使用，如 {@code user} 模块在分配用户岗位前
     * 通过 {@code OrganizationFacade.existsByIds(ids)} 校验岗位 ID 有效性，
     * 该 Facade 内部会调用本方法。返回顺序不保证与入参一致。
     *
     * @param ids 岗位 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除的岗位列表，顺序不保证与入参一致
     */
    List<PositionDO> listByIds(List<Long> ids);

    /**
     * 分页查询岗位，按 {@code sortOrder}、{@code id} 升序排列。
     *
     * <p>岗位是平铺列表，没有层级关系，用分页而非树形返回。
     *
     * <p><b>参数校验：</b>实现类会校验 {@code pageSize} 的范围
     * （{@code 1 ~ PageParam.MAX_PAGE_SIZE}），超出时抛
     * {@link IllegalArgumentException}。调用方不应假设本方法会接受
     * 任意 {@code pageSize}。
     *
     * <p>分页与排序完全委托给仓储层——岗位是平铺列表，不需要 Service
     * 层做额外的结构组装。仓储层在 SQL 中按 {@code sort_order}、
     * {@code id} 升序排列。
     *
     * @param pageParam    分页参数
     * @param positionName 岗位名称模糊匹配；为 {@code null} 或空白时不过滤
     * @param status       状态精确匹配；为 {@code null} 时不过滤
     * @return 分页结果
     */
    PageResult<PositionDO> page(PageParam pageParam, String positionName, Integer status);

    // ==================== 命令 ====================

    /**
     * 创建岗位。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验 {@code positionCode} 在未删除岗位中唯一——它是租户内
     *       唯一的业务标识</li>
     *   <li>校验 {@code positionName} 在未删除岗位中唯一</li>
     * </ul>
     *
     * <p>逻辑删除辅助列 {@code activePositionCode} / {@code activePositionName}
     * 由 {@code PositionRepositoryImpl.save} 维护，不在本方法中设置。
     *
     * <p><b>并发下前置校验不可靠：</b>两个并发请求可能同时通过唯一性校验，
     * 最终依赖数据库的 {@code uk_iam_position_tenant_active_position_code} /
     * {@code uk_iam_position_tenant_active_position_name} 唯一约束兜底。
     * 冲突时抛 {@code DuplicateKeyException}，由全局异常处理器转为友好错误。
     *
     * @param position 待创建的岗位
     * @return 新岗位的 ID
     * @throws IllegalArgumentException 编码或名称重复时抛出
     */
    Long createPosition(PositionDO position);

    /**
     * 更新岗位。
     *
     * <p>不修改 {@code positionCode}：它是业务标识，创建后不可修改，
     * 被 {@code iam_user_position} 等关联表引用时若变更会导致追溯困难。
     * 即使调用方在 {@code position} 中传入了不同的 {@code positionCode}，
     * 也会被数据库中的原值覆盖。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验岗位存在</li>
     *   <li>校验 {@code positionName} 唯一性，<b>排除自身</b></li>
     * </ul>
     *
     * @param position 待更新的岗位，必须包含有效 {@code id}
     * @throws IllegalArgumentException 岗位不存在、名称与其他岗位冲突时抛出
     */
    void updatePosition(PositionDO position);

    /**
     * 变更岗位状态（启用 / 停用）。
     *
     * <p>状态变更后发布 {@code PositionStatusChangedEvent}，供其他模块
     * （如缓存刷新、审计）响应。状态未实际变化时不发布事件、不写库——
     * 避免订阅方收到“状态从 1 变为 1”这类无意义的通知。
     *
     * @param id     岗位 ID
     * @param status 新状态：0-正常、1-停用
     * @throws IllegalArgumentException 岗位不存在或状态值非法时抛出
     */
    void changeStatus(Long id, Integer status);

    /**
     * 删除岗位。
     *
     * <p>本方法只校验岗位自身存在，<b>不</b>同步校验岗位是否被用户引用。
     * {@code iam_user_position} 归 {@code user} 模块所有，本模块查询它会形成
     * 反向数据依赖。用户侧的关联由 {@code user} 模块监听
     * {@code PositionDeletedEvent} 异步清理，最终一致。
     *
     * <p>如果业务确实要求“被引用的岗位不允许删除”，需要先在 ADR 中
     * 重新界定 {@code iam_user_position} 的所有权——要么划给
     * {@code organization} 模块，要么由 {@code user} 模块通过
     * {@code OrganizationFacade} 暴露一个只读的“是否被引用”查询接口，
     * 而不是让 {@code organization} 直接查 {@code user} 的表。
     *
     * @param id 岗位 ID
     * @throws IllegalArgumentException 岗位不存在时抛出
     */
    void deletePosition(Long id);
}
