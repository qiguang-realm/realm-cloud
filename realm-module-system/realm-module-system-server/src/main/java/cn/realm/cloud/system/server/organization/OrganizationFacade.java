package cn.realm.cloud.system.server.organization;

import java.util.List;
import java.util.Optional;

/**
 * 组织架构模块的跨模块门面。
 *
 * <p>本接口是 {@code organization} 模块对外的<b>只读</b>能力边界。其他模块
 * （如 {@code role} 校验自定义数据范围的部门有效性、{@code user} 校验用户的
 * 所属部门）通过注入本接口获取部门信息，<b>不</b>直接引用
 * {@code organization.internal} 中的 {@code DepartmentService}、
 * {@code DepartmentRepository} 或 {@code DepartmentDO}。
 * Spring Modulith 的 {@code verify()} 会阻止跨模块引用 {@code internal} 类型。
 *
 * <p><b>为什么只有只读方法：</b>部门的创建、更新、状态变更、删除是由
 * HTTP 适配器（{@code DepartmentAdminController}）触发的用例，跨模块
 * 不应绕过用例层直接写部门数据。其他模块需要“部门发生了某事”时，
 * 应订阅 {@code organization.events} 下的事件，而不是调用写方法。
 *
 * <p><b>返回类型：</b>所有方法返回 {@link DepartmentDto} 或 {@code boolean}
 * 等中性类型，不暴露 {@link cn.realm.cloud.system.server.organization.internal.persistence.DepartmentDO}。
 * {@code DepartmentDto} 是模块公开契约的一部分，字段变更需要评估对其他模块的影响。
 *
 * <p><b>实现类：</b>{@code OrganizationFacadeImpl} 位于 {@code internal} 包，
 * 由 {@code verify()} 保护，其他模块无法直接引用。
 *
 * @author QI Guang
 */
public interface OrganizationFacade {

    /**
     * 判断部门是否存在（未删除）。
     *
     * <p>供其他模块在写入关联前做存在性校验，例如
     * {@code user} 模块校验 {@code UserDO.departmentId} 是否有效。
     *
     * @param id 部门 ID；为 {@code null} 时返回 {@code false}
     * @return 存在未删除的部门时为 {@code true}
     */
    boolean existsById(Long id);

    /**
     * 批量判断部门是否全部存在（未删除）。
     *
     * <p>供其他模块在批量写入关联前做校验，例如
     * {@code role} 模块在设置自定义数据范围前确认这些部门都有效。
     *
     * <p>返回 {@code true} 当且仅当 {@code ids} 中的每个 ID 都对应
     * 一个未删除的部门。{@code ids} 为空时返回 {@code true}
     * （空集合视为“无需校验”）。
     *
     * @param ids 部门 ID 列表；为 {@code null} 或空集合时返回 {@code true}
     * @return 全部存在时为 {@code true}，任一不存在时为 {@code false}
     */
    boolean existsByIds(List<Long> ids);

    /**
     * 按 ID 查询部门只读信息。
     *
     * <p>供其他模块读取部门的基本信息（名称、父部门、状态），
     * 用于展示或业务判断。返回的是 {@link DepartmentDto}，不包含
     * 审计字段、逻辑删除标记等内部字段。
     *
     * @param id 部门 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除部门的只读信息；不存在或已逻辑删除时返回空
     */
    Optional<DepartmentDto> findById(Long id);

    /**
     * 按主键批量查询部门只读信息。
     *
     * <p>供其他模块做批量展示或关联查询，例如 {@code user} 模块在
     * 用户列表接口中批量查询部门名称。
     *
     * @param ids 部门 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除部门的只读信息列表，顺序不保证与入参一致
     */
    List<DepartmentDto> findByIds(List<Long> ids);
}
