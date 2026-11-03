package cn.realm.cloud.system.server.role.internal.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 角色-菜单关联仓储接口。
 *
 * <p>方法签名使用业务语义，不暴露 MyBatis-Plus 的 {@code Wrapper}，
 * 使 {@code internal.application} 层与具体 ORM 解耦。
 *
 * <p><b>关联表的特殊约定：</b>{@code RoleMenuDO} 没有 {@code isDeleted}
 * 字段，也没有逻辑删除辅助列——它是“只增删、不更新”的关联表，
 * 删除即物理删除，不存在“已删除但仍在表中”的记录。
 * 因此本接口的所有方法<b>不需要</b>加 {@code is_deleted = 0} 条件，
 * 这与 {@code MenuRepository} / {@code UserRepository} 等聚合根仓储不同。
 *
 * <p><b>写入语义：</b>只有 {@link #replaceByRoleId(Long, List)} 一种写入方式，
 * 不做“逐条 add/remove”的增量接口。全量替换让调用方只需提交最终状态，
 * 避免并发下“两个请求各自增删一半”的中间态。
 *
 * <p><b>唯一索引要求：</b>{@code iam_role_menu} 必须有
 * {@code (role_id, menu_id)} 唯一索引。这是 {@code replaceByRoleId}
 * 在并发场景下不产生重复行的前提——没有唯一索引时，两次并发的全量替换
 * 可能交错执行，最终产生重复关联。
 *
 * <p>实现由 {@link RoleMenuRepositoryImpl} 提供，MyBatis-Plus 的
 * {@code RoleMenuMapper} 仅在该实现类内部使用。
 *
 * @author QI Guang
 */
public interface RoleMenuRepository {

    /**
     * 查询单个角色已分配的菜单 ID 列表。
     *
     * <p>返回顺序按关联记录的主键升序，保证结果确定（便于测试与排查）。
     * 业务上顺序不重要——前端通常用集合处理，再与菜单树做交集。
     *
     * <p><b>使用场景：</b>角色编辑页回显菜单勾选状态。
     *
     * @param roleId 角色 ID；为 {@code null} 时返回空列表
     * @return 菜单 ID 列表；无关联时返回空列表
     */
    List<Long> findMenuIdsByRoleId(Long roleId);

    /**
     * 批量查询角色授权的菜单 ID 集合。
     *
     * <p>一次 {@code IN} 查询，返回给定角色通过 {@code iam_role_menu}
     * 授权的所有菜单 ID 的<b>并集</b>——同一菜单被多个角色授权时只出现一次。
     *
     * <p><b>使用场景：</b>登录时聚合权限点（{@code UserFacade.findPermissionsByUserId}
     * → {@code RoleFacade.findMenuIdsByRoleIds}）。与
     * {@link #findMenuIdsByRoleId(Long)} 的区别：后者查单个角色（供回显），
     * 本方法查一批角色的并集（供聚合）。
     *
     * <p><b>为什么返回 {@code Set} 而不是 {@code List}：</b>
     * 并集语义下，同一菜单被多个角色授权时只应出现一次。用 {@code Set}
     * 天然去重——{@code (role_id, menu_id)} 唯一索引保证单角色内不重复，
     * 但跨角色（同一用户持有多个角色，不同角色授权了同一菜单）会有重复，
     * {@code Set} 收集正好处理这种重复，调用方不需要再处理。
     *
     * <p><b>不排序：</b>集合无序，调用方如需要确定性顺序应自行排序。
     * 单角色版本的 `orderByAsc` 是为回显场景保证顺序，批量版本不承担这个职责。
     *
     * <p><b>不校验角色是否存在：</b>悬空 {@code roleId}（角色已删除但关联未清理）
     * 不报错，只是查不到关联、不贡献菜单 ID。这是刻意的“宽容查询”——
     * 单个悬空 ID 不应阻断整个用户的权限点计算。
     *
     * @param roleIds 角色 ID 集合；为 {@code null} 或空集合时返回空集合
     * @return 菜单 ID 集合；无授权时返回空集合
     */
    Set<Long> findMenuIdsByRoleIds(Collection<Long> roleIds);

    /**
     * 全量替换角色的菜单关联：先删除该角色的所有关联，再插入新列表。
     *
     * <p><b>必须在事务内调用</b>（由 {@code RoleMenuService} 的
     * {@code @Transactional} 保证）。删除与插入之间如果失败，整体回滚，
     * 不会留下“删了旧关联但没插入新关联”的中间态。
     *
     * <p>{@code menuIds} 为空或 {@code null} 时等价于清空——
     * 先删除所有关联，不插入任何记录。这是“清空菜单授权”的合法操作。
     *
     * <p><b>去重职责：</b>调用方（Service 层）应已去重。本实现不再去重，
     * 以避免掩盖上游的数据质量问题；重复 ID 由
     * {@code (role_id, menu_id)} 唯一索引兜底，抛
     * {@code DuplicateKeyException}。
     *
     * <p><b>审计字段：</b>插入的记录只设置 {@code roleId} 和 {@code menuId}，
     * {@code tenantId} / {@code createBy} / {@code createTime} 由框架的
     * 租户拦截器和 {@code MetaObjectHandler} 自动填充。如果项目未配置
     * 自动填充，这三个字段会是 {@code null}，需要在本实现中显式设置。
     *
     * @param roleId  角色 ID；为 {@code null} 时不做任何操作
     * @param menuIds 菜单 ID 列表；{@code null} 或空列表表示清空
     */
    void replaceByRoleId(Long roleId, List<Long> menuIds);

    /**
     * 删除指定角色的所有菜单关联。
     *
     * <p>供 {@code RoleServiceImpl.deleteRole} 在删除角色前清理使用。
     * 关联表归 {@code role} 模块所有，删除角色时直接清理，不需要事件机制。
     *
     * <p>幂等：角色不存在或无关联时调用不报错，影响 0 行。
     *
     * @param roleId 角色 ID；为 {@code null} 时不做任何操作
     */
    void deleteByRoleId(Long roleId);

    /**
     * 删除指定菜单的所有角色关联。
     *
     * <p>供 {@code MenuDeletedListener} 在菜单删除后清理使用。
     * 这是 {@code menu → events → role} 单向依赖链的一部分：
     * {@code menu} 删除菜单时只发布事件，{@code role} 模块作为关联表的
     * 持有方，订阅事件后自行清理。
     *
     * <p>幂等：菜单不存在或无关联时调用不报错，影响 0 行。
     *
     * @param menuId 菜单 ID；为 {@code null} 时不做任何操作
     */
    void deleteByMenuId(Long menuId);
}
