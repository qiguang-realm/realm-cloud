package cn.realm.cloud.system.server.organization.internal.persistence;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.organization.internal.persistence.mapper.PositionMapper;

import java.util.List;
import java.util.Optional;

/**
 * 岗位仓储接口。
 *
 * <p>方法签名使用业务语义，不暴露 MyBatis-Plus 的 {@code Wrapper}、{@code IPage}，
 * 使 {@code internal.application} 层与具体 ORM 解耦。
 *
 * <p><b>职责边界：</b>
 * <ul>
 *   <li><b>本接口只负责持久化读写</b>，不承担业务规则：
 *       {@code positionCode} / {@code positionName} 的唯一性校验、状态值校验、
 *       删除前是否被用户引用的校验，均由应用服务在事务内编排</li>
 *   <li><b>所有查询（含 {@link #findById}、{@link #findByIds}）默认只返回
 *       未逻辑删除（{@code isDeleted = 0}）的记录</b>。{@code PositionDO} 未标注
 *       {@code @TableLogic}，因此实现必须显式追加 {@code is_deleted = 0} 条件，
 *       不能依赖 MyBatis-Plus 的自动逻辑删除</li>
 *   <li>方法对空入参（{@code null} / 空集合 / 空白字符串）返回空结果而非抛异常，
 *       调用方无需为每个调用点重复判空</li>
 * </ul>
 *
 * <p><b>参数校验职责：</b>本接口不校验 {@code pageSize} 的范围——
 * 校验由 {@code PositionServiceImpl.page} 保证（HTTP 入口由
 * {@code PositionPageRequest} 的 {@code @Min} / {@code @Max} 拦截，
 * 内部调用由 Service 层显式校验）。本接口信任入参已合法，
 * 实现直接委托 {@code BaseMapperX.selectPage}。
 *
 * <p><b>两个逻辑删除辅助列：</b>{@code activePositionCode} 和
 * {@code activePositionName} 是 {@code position_code} / {@code position_name}
 * 在“未删除岗位中唯一”的实现手段。实现必须在 {@link #save} 的更新分支
 * 和 {@link #deleteById} 中<b>同时</b>显式维护这两个列——漏一个，
 * 对应的唯一约束就不会释放，新建同名/同编码岗位会撞约束。
 * 这是 {@code PositionDO} 与 {@code RoleDO} 共有的特征——
 * 两个辅助列，比 {@code MenuDO} / {@code DepartmentDO}（各一个）更容易出错。
 *
 * <p>实现由 {@code PositionRepositoryImpl} 提供，MyBatis-Plus 的
 * {@link PositionMapper} 仅在该实现类内部使用。
 *
 * @author QI Guang
 */
public interface PositionRepository {

    // ==================== 查询 ====================

    /**
     * 按主键查询未删除的岗位。
     *
     * @param id 岗位 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除的岗位；不存在或已逻辑删除时返回空
     */
    Optional<PositionDO> findById(Long id);

    /**
     * 按主键批量查询未删除的岗位。
     *
     * @param ids 岗位 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除的岗位列表，顺序不保证与入参一致
     */
    List<PositionDO> findByIds(List<Long> ids);

    /**
     * 分页查询未删除的岗位，按 {@code sortOrder}、{@code id} 升序排列。
     *
     * <p>岗位是平铺列表，没有层级关系，用分页而非树形返回。
     *
     * <p><b>参数校验：</b>本方法信任 {@code pageParam} 已合法——
     * {@code pageSize} 的取值范围由 {@code PositionServiceImpl.page} 保证。
     * 实现直接委托 {@code BaseMapperX.selectPage(PageParam, Wrapper)}，
     * 不做额外的范围检查。
     *
     * <p>过滤条件：
     * <ul>
     *   <li>{@code positionName} 模糊匹配；为 {@code null} 或空白时不过滤</li>
     *   <li>{@code status} 精确匹配；为 {@code null} 时不过滤</li>
     * </ul>
     *
     * @param pageParam    分页参数
     * @param positionName 岗位名称模糊匹配；为 {@code null} 或空白时不过滤
     * @param status       状态精确匹配；为 {@code null} 时不过滤
     * @return 分页结果
     */
    PageResult<PositionDO> page(PageParam pageParam, String positionName, Integer status);

    // ==================== 存在性判断 ====================

    /**
     * 判断岗位编码是否已被未删除的岗位占用。
     *
     * @param positionCode 岗位编码；为 {@code null} 或空白时返回 {@code false}
     */
    boolean existsByPositionCode(String positionCode);

    /**
     * 判断岗位编码是否已被<b>除指定 ID 之外</b>的未删除岗位占用。
     *
     * <p>供更新场景使用，排除自身，避免把未改动的当前值判为冲突。
     * 当前 {@code positionCode} 创建后不可修改，更新时不会触发冲突，
     * 但保留本方法以便未来放宽约束时复用。
     *
     * @param positionCode 岗位编码；为 {@code null} 或空白时返回 {@code false}
     * @param excludeId    排除的岗位 ID；为 {@code null} 时等价于
     *                     {@link #existsByPositionCode(String)}
     */
    boolean existsByPositionCodeAndIdNot(String positionCode, Long excludeId);

    /**
     * 判断岗位名称是否已被未删除的岗位占用。
     *
     * @param positionName 岗位名称；为 {@code null} 或空白时返回 {@code false}
     */
    boolean existsByPositionName(String positionName);

    /**
     * 判断岗位名称是否已被<b>除指定 ID 之外</b>的未删除岗位占用。
     *
     * <p>供更新场景使用，排除自身，避免把未改动的当前值判为冲突。
     *
     * @param positionName 岗位名称；为 {@code null} 或空白时返回 {@code false}
     * @param excludeId    排除的岗位 ID；为 {@code null} 时等价于
     *                     {@link #existsByPositionName(String)}
     */
    boolean existsByPositionNameAndIdNot(String positionName, Long excludeId);

    // ==================== 写入 ====================

    /**
     * 保存岗位：{@code id} 为 {@code null} 时插入，否则更新。
     *
     * <p>实现必须同步维护<b>两个</b>逻辑删除下的唯一约束辅助列：
     * <ul>
     *   <li>{@code activePositionCode}：未删除时等于 {@code positionCode}，否则为 {@code null}</li>
     *   <li>{@code activePositionName}：未删除时等于 {@code positionName}，否则为 {@code null}</li>
     * </ul>
     * <b>注意：</b>MyBatis-Plus 默认的 {@code updateById} 会忽略 {@code null} 字段，
     * 如果本次更新要把某个业务列置空，必须用 {@code UpdateWrapper} 显式
     * {@code set} 为 {@code null}，否则辅助列会保留旧值、唯一约束不释放。
     *
     * <p><b>更新分支必须显式带 {@code is_deleted = 0} 条件</b>——
     * 否则调用方传入一个已逻辑删除的 {@code id}，会把该记录“改活”。
     *
     * <p><b>更新分支的字段范围：</b>只更新“岗位资料”相关字段，
     * <b>不更新</b>以下字段：
     * <ul>
     *   <li>{@code positionCode}——业务标识，创建后不可修改，
     *       被 {@code iam_user_position} 等关联表引用时若变更会导致追溯困难。
     *       {@code PositionServiceImpl.updatePosition} 已用数据库原值覆盖，
     *       Repository 层不 set 是第二重保护</li>
     *   <li>{@code tenantId}——岗位不能跨租户迁移</li>
     *   <li>{@code createBy} / {@code createTime}——审计字段，创建后不变</li>
     *   <li>{@code version}——乐观锁版本号，当前未启用乐观锁</li>
     * </ul>
     *
     * @param position 待保存的岗位，不能为 {@code null}
     */
    void save(PositionDO position);

    /**
     * 逻辑删除岗位。
     *
     * <p>实现必须将 {@code isDeleted} 置为 {@code 1}，<b>并显式将两个辅助列
     * {@code activePositionCode} / {@code activePositionName} 置为 {@code null}</b>
     * 以释放唯一约束。由于 {@code updateById} 忽略 {@code null} 字段，
     * 这里必须使用 {@code UpdateWrapper} 或 {@code LambdaUpdateWrapper}
     * 显式 {@code set}。
     *
     * <p>本方法不做“是否允许删除”的业务判断——岗位是否被用户引用、
     * 是否应拒绝删除，由应用服务在调用前决定。也不清理跨模块关联
     * （{@code iam_user_position} 归 {@code user} 模块所有），
     * 跨模块清理由 {@code PositionDeletedEvent} 的订阅方异步完成。
     *
     * @param id 岗位 ID；为 {@code null} 时不做任何操作
     */
    void deleteById(Long id);
}
