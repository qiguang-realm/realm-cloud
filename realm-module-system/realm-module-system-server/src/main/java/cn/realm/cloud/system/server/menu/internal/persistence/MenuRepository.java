package cn.realm.cloud.system.server.menu.internal.persistence;

import cn.realm.cloud.system.server.menu.internal.persistence.mapper.MenuMapper;

import java.util.List;
import java.util.Optional;

/**
 * 菜单仓储接口。
 *
 * <p>方法签名使用业务语义，不暴露 MyBatis-Plus 的 {@code Wrapper}、{@code IPage}，
 * 使 {@code internal.application} 层与具体 ORM 解耦。
 *
 * <p><b>职责边界：</b>
 * <ul>
 *   <li><b>本接口只负责持久化读写</b>，不承担业务规则：
 *       {@code ancestors} 的拼接、移动节点时子孙链的重算、
 *       删除前是否有子节点或角色引用的校验，均由应用服务在事务内编排</li>
 *   <li><b>所有查询（含 {@link #findById}、{@link #findByIds}）默认只返回
 *       未逻辑删除（{@code isDeleted = 0}）的记录</b>。{@code MenuDO} 未标注
 *       {@code @TableLogic}，因此实现必须显式追加 {@code is_deleted = 0}
 *       条件，不能依赖 MyBatis-Plus 的自动逻辑删除</li>
 *   <li>方法对空入参（{@code null} / 空集合 / 空白字符串）返回空结果而非抛异常，
 *       调用方无需为每个调用点重复判空</li>
 * </ul>
 *
 * <p>实现由 {@code MenuRepositoryImpl} 提供，MyBatis-Plus 的 {@link MenuMapper}
 * 仅在该实现类内部使用。
 *
 * @author QI Guang
 */
public interface MenuRepository {

    // ==================== 查询 ====================

    /**
     * 按主键查询未删除的菜单。
     *
     * @param id 菜单 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除的菜单；不存在或已逻辑删除时返回空
     */
    Optional<MenuDO> findById(Long id);

    /**
     * 按权限标识查询未删除的菜单。
     *
     * <p><b>当前 {@code MenuServiceImpl} 未使用本方法</b>——权限标识的唯一性
     * 校验走 {@link #existsByPermissionCode(String)} /
     * {@link #existsByPermissionCodeAndIdNot(String, Long)}，不需要查出完整记录。
     * 保留本方法是为了给“需要按权限标识查菜单详情”的场景留一个入口，
     * 如审计追溯、权限点排查。如果全局搜索确认无调用方，可以删除——
     * 没有调用点的公开方法会让后来者误以为系统在用它。
     *
     * @param permissionCode 权限标识；为 {@code null} 或空白时返回 {@link Optional#empty()}
     * @return 未删除的菜单；不存在时返回空
     */
    Optional<MenuDO> findByPermissionCode(String permissionCode);

    /**
     * 按主键批量查询未删除的菜单。
     *
     * @param ids 菜单 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除的菜单列表，顺序不保证与入参一致
     */
    List<MenuDO> findByIds(List<Long> ids);

    /**
     * 查询全部未删除菜单，按 {@code sortOrder}、{@code id} 升序排列。
     *
     * <p><b>排序注意：</b>SQL 的 {@code ORDER BY sort_order} 在 MySQL 下
     * 把 {@code NULL} 排在最前，与业务期望的“排末尾”相反。
     * {@code MenuServiceImpl.listAll()} 会在 Service 层用
     * {@code Comparator.nullsLast} 重排修正这个差异——本方法只保证
     * “有确定顺序”，不保证 {@code NULL} 的位置符合业务期望。
     */
    List<MenuDO> findAll();

    /**
     * 按菜单类型查询未删除菜单，按 {@code sortOrder}、{@code id} 升序排列。
     *
     * @param menuType 菜单类型：1-目录、2-菜单、3-按钮；
     *                 为 {@code null} 时等价于 {@link #findAll()}
     */
    List<MenuDO> findByType(Integer menuType);

    /**
     * 查询指定父节点下的直接子菜单（未删除）。
     *
     * @param parentId 父节点 ID；顶级节点的子菜单其 {@code parentId} 为 {@code 0}
     */
    List<MenuDO> findByParentId(Long parentId);

    /**
     * 查询祖先链以给定前缀开头的所有子孙节点（未删除）。
     *
     * <p>实现必须以 {@code prefix + ","} 作为 LIKE 的前缀，避免
     * {@code "0,1,5"} 误匹配 {@code "0,1,50"} 这类节点。
     *
     * @param ancestorsPrefix 祖先链前缀，如 {@code "0,1,5"}；
     *                        为 {@code null} 或空白时返回空列表
     */
    List<MenuDO> findByAncestorsPrefix(String ancestorsPrefix);

    // ==================== 存在性判断 ====================

    /**
     * 判断权限标识是否已被未删除的菜单占用。
     *
     * @param permissionCode 权限标识；为 {@code null} 或空白时返回 {@code false}
     */
    boolean existsByPermissionCode(String permissionCode);

    /**
     * 判断权限标识是否已被<b>除指定 ID 之外</b>的未删除菜单占用。
     *
     * <p>供更新场景使用，排除自身，避免把未改动的当前值判为冲突。
     *
     * @param permissionCode 权限标识；为 {@code null} 或空白时返回 {@code false}
     * @param excludeId      排除的菜单 ID；为 {@code null} 时等价于
     *                       {@link #existsByPermissionCode(String)}
     */
    boolean existsByPermissionCodeAndIdNot(String permissionCode, Long excludeId);

    /**
     * 判断指定父节点下是否存在未删除的子菜单。
     *
     * <p>删除菜单前的守卫：有子菜单时应拒绝删除，避免留下孤儿节点
     * 和断裂的 {@code ancestors} 链。
     *
     * <p><b>并发说明：</b>“检查子菜单”与“删除”之间存在竞态窗口——
     * 并发创建子菜单时可能在检查通过后、删除提交前插入一条记录，
     * 导致孤儿节点。当前实现未对此加锁；如需强一致，应在数据库层
     * 加 {@code parent_id} 外键，或对菜单行加 {@code SELECT ... FOR UPDATE}。
     *
     * @param parentId 父节点 ID；为 {@code null} 时返回 {@code false}
     */
    boolean existsByParentId(Long parentId);

    // ==================== 写入 ====================

    /**
     * 保存菜单：{@code id} 为 {@code null} 时插入，否则更新。
     *
     * <p>实现必须同步维护逻辑删除下的唯一约束辅助列
     * {@code activePermissionCode}：未删除时等于 {@code permissionCode}，
     * 否则为 {@code null}。<b>注意：</b>MyBatis-Plus 默认的 {@code updateById}
     * 会忽略 {@code null} 字段，如果本次更新要把 {@code permissionCode}
     * 置空，必须用 {@code UpdateWrapper} 显式 {@code set} 为 {@code null}，
     * 否则辅助列会保留旧值、唯一约束不释放。
     *
     * <p><b>更新分支的字段范围：</b>只更新“菜单资料”相关字段，
     * <b>不更新</b>以下字段：
     * <ul>
     *   <li>{@code menuType}——结构性属性，类型变更需要额外的规则校验，
     *       不复用普通编辑。{@code MenuServiceImpl.updateMenu} 已用数据库
     *       原值覆盖，Repository 层不 set 是第二重保护</li>
     *   <li>{@code parentId} / {@code ancestors}——移动节点由独立的 move
     *       用例处理，{@code ancestors} 的更新走
     *       {@link #updateAncestorsBatch(List)}，不走 {@code save}。
     *       Service 层已用数据库原值覆盖，Repository 层不 set 是第二重保护</li>
     *   <li>{@code tenantId}——菜单不能跨租户迁移</li>
     *   <li>{@code createBy} / {@code createTime}——审计字段，创建后不变</li>
     *   <li>{@code version}——乐观锁版本号，当前未启用乐观锁</li>
     * </ul>
     *
     * <p>本方法不负责 {@code ancestors} 的计算——调用方必须在调用前
     * 根据父节点设置好 {@code ancestors}。
     *
     * @param menu 待保存的菜单，不能为 {@code null}
     */
    void save(MenuDO menu);

    /**
     * 批量更新子孙节点的祖先链。
     *
     * <p>用于移动节点后重算整棵子树的 {@code ancestors}。实现应只更新
     * {@code ancestors} 字段，不整行回写，避免覆盖并发修改的其他字段。
     *
     * <p><b>当前实现为逐条更新</b>，适合单次移动影响的子孙数量较小的场景
     * （菜单树通常深度有限、单节点子孙数不大）。如果未来出现“移动一个
     * 有上百子孙的目录”的需求，应改为 {@code CASE WHEN} 批量更新或
     * 分批 {@code IN} 更新。
     *
     * @param menus 待更新的节点列表，每个节点需携带 {@code id} 和新的 {@code ancestors}；
     *              为 {@code null} 或空集合时不做任何操作
     */
    void updateAncestorsBatch(List<MenuDO> menus);

    /**
     * 逻辑删除菜单。
     *
     * <p>实现必须将 {@code isDeleted} 置为 {@code 1}，<b>并显式将辅助列
     * {@code activePermissionCode} 置为 {@code null}</b> 以释放唯一约束。
     * 由于 {@code updateById} 忽略 {@code null} 字段，这里必须使用
     * {@code UpdateWrapper} 或 {@code LambdaUpdateWrapper} 显式 {@code set}。
     *
     * <p>本方法不做“是否允许删除”的业务判断——子菜单存在性、角色引用等
     * 校验由应用服务在调用前完成。
     *
     * @param id 菜单 ID；为 {@code null} 时不做任何操作
     */
    void deleteById(Long id);
}
