package cn.realm.cloud.system.server.role.internal.persistence;

import java.util.List;

/**
 * 角色-部门关联仓储接口。
 *
 * <p>方法签名使用业务语义，不暴露 MyBatis-Plus 的 {@code Wrapper}，
 * 使 {@code internal.application} 层与具体 ORM 解耦。
 *
 * <p><b>关联表的特殊约定：</b>{@code RoleDepartmentDO} 没有
 * {@code isDeleted} 字段，也没有逻辑删除辅助列——它是“只增删、不更新”的
 * 关联表，删除即物理删除，不存在“已删除但仍在表中”的记录。
 * 因此本接口的所有方法<b>不需要</b>加 {@code is_deleted = 0} 条件，
 * 这与 {@code MenuRepository} / {@code UserRepository} 等聚合根仓储不同。
 *
 * <p><b>写入语义：</b>只有 {@link #replaceByRoleId(Long, List)} 一种写入方式，
 * 不做“逐条 add/remove”的增量接口。全量替换让调用方只需提交最终状态，
 * 避免并发下“两个请求各自增删一半”的中间态。
 *
 * <p><b>唯一索引要求：</b>{@code iam_role_department} 必须有
 * {@code (role_id, department_id)} 唯一索引。这是 {@code replaceByRoleId}
 * 在并发场景下不产生重复行的前提——没有唯一索引时，两次并发的全量替换
 * 可能交错执行，最终产生重复关联。
 *
 * <p>实现由 {@link RoleDepartmentRepositoryImpl} 提供，MyBatis-Plus 的
 * {@code RoleDepartmentMapper} 仅在该实现类内部使用。
 *
 * @author QI Guang
 */
public interface RoleDepartmentRepository {

    /**
     * 查询角色自定义数据范围的部门 ID 列表。
     *
     * <p>返回顺序按关联记录的主键升序，保证结果确定（便于测试与排查）。
     * 业务上顺序不重要——前端通常用集合处理。
     *
     * @param roleId 角色 ID；为 {@code null} 时返回空列表
     * @return 部门 ID 列表；无关联时返回空列表
     */
    List<Long> findDepartmentIdsByRoleId(Long roleId);

    /**
     * 全量替换角色的部门关联：先删除该角色的所有关联，再插入新列表。
     *
     * <p><b>必须在事务内调用</b>（由 {@code RoleDepartmentService} 的
     * {@code @Transactional} 保证）。删除与插入之间如果失败，整体回滚，
     * 不会留下“删了旧关联但没插入新关联”的中间态。
     *
     * <p>{@code departmentIds} 为空或 {@code null} 时等价于清空——
     * 先删除所有关联，不插入任何记录。这是“清空自定义范围”的合法操作。
     *
     * <p><b>去重职责：</b>调用方（Service 层）应已去重。本实现不再去重，
     * 以避免掩盖上游的数据质量问题；重复 ID 由
     * {@code (role_id, department_id)} 唯一索引兜底，抛
     * {@code DuplicateKeyException}。
     *
     * <p><b>审计字段：</b>插入的记录只设置 {@code roleId} 和
     * {@code departmentId}，{@code tenantId} / {@code createBy} /
     * {@code createTime} 由框架的租户拦截器和 {@code MetaObjectHandler}
     * 自动填充。如果项目未配置自动填充，这三个字段会是 {@code null}，
     * 需要在本实现中显式设置。
     *
     * @param roleId        角色 ID；为 {@code null} 时不做任何操作
     * @param departmentIds 部门 ID 列表；{@code null} 或空列表表示清空
     */
    void replaceByRoleId(Long roleId, List<Long> departmentIds);

    /**
     * 删除指定角色的所有部门关联。
     *
     * <p>供两个场景使用：
     * <ul>
     *   <li>{@code RoleServiceImpl.deleteRole}——删除角色前清理</li>
     *   <li>{@code RoleServiceImpl.updateRole}——{@code dataScope} 从自定义
     *       改为其他值时清理残留</li>
     * </ul>
     *
     * <p>幂等：角色不存在或无关联时调用不报错，影响 0 行。
     *
     * @param roleId 角色 ID；为 {@code null} 时不做任何操作
     */
    void deleteByRoleId(Long roleId);

    /**
     * 删除指定部门的所有角色关联。
     *
     * <p>供 {@code DepartmentDeletedListener} 在部门删除后清理使用。
     * 这是 {@code organization → events → role} 单向依赖链的一部分：
     * {@code organization} 删除部门时只发布事件，{@code role} 模块
     * 作为关联表的持有方，订阅事件后自行清理。
     *
     * <p>幂等：部门不存在或无关联时调用不报错，影响 0 行。
     *
     * @param departmentId 部门 ID；为 {@code null} 时不做任何操作
     */
    void deleteByDepartmentId(Long departmentId);
}
