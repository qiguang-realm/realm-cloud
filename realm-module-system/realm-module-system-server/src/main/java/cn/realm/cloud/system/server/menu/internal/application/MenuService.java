package cn.realm.cloud.system.server.menu.internal.application;

import cn.realm.cloud.system.server.menu.internal.api.web.admin.dto.MenuResponse;
import cn.realm.cloud.system.server.menu.internal.persistence.MenuDO;

import java.util.List;
import java.util.Optional;

/**
 * 菜单模块应用服务。
 *
 * <p>编排菜单的用例：创建、更新、树形查询、状态变更、删除。
 * 负责以下业务规则——这些规则不进入 {@code MenuRepository}，仓储只负责数据存取：
 * <ul>
 *   <li>{@code ancestors} 的拼接与子孙链重算</li>
 *   <li>删除前的子菜单校验</li>
 *   <li>按菜单类型的必填字段校验</li>
 *   <li>权限标识唯一性校验</li>
 * </ul>
 *
 * <p><b>模块可见性：</b>本接口位于 {@code internal} 包，<b>不暴露给其他模块</b>。
 * 其他模块需要菜单能力（如 {@code role} 分配菜单时校验菜单存在性）时，
 * 通过模块根包的 {@code MenuFacade} 访问，而不是注入本接口。
 * Spring Modulith 的 {@code verify()} 会阻止跨模块引用 {@code internal} 类型。
 *
 * <p><b>查询方法的分工：</b>
 * <ul>
 *   <li>{@link #getMenu(Long)}——严格查询，不存在时抛异常，供业务用例使用
 *       （菜单不存在说明调用方的假设有误）</li>
 *   <li>{@link #findById(Long)}——宽容查询，不存在时返回空，供
 *       {@code MenuFacade} 使用（菜单不存在是正常的业务边界，如 role 侧
 *       清理悬空关联时查询已删除的菜单）</li>
 * </ul>
 *
 * <p><b>删除语义（与部门模块一致）：</b>{@link #deleteMenu(Long)}
 * 只校验是否存在未删除的子菜单，<b>不</b>查询 {@code iam_role_menu} 表校验
 * 是否被角色引用——该表归 {@code role} 模块所有，本模块查询它会形成
 * 反向数据依赖。角色侧的关联由 {@code role} 模块监听 {@code MenuDeletedEvent}
 * 异步清理，最终一致。在清理完成之前，role 侧可能短暂持有指向已删除菜单的
 * ID——订阅方查询时应容忍查不到对应菜单的情况，清理逻辑须幂等。
 *
 * <p><b>分层说明：</b>{@link #listTree(Integer)} 返回 {@link MenuResponse}，
 * 使本接口依赖 {@code internal.api.web.admin.dto} 中的类型。这在
 * {@code internal} 包内部不构成模块边界违规，属于“树组装放在 Service
 * 比放在 Controller 更合适”的权衡——与 {@code DepartmentService.listTree}
 * 保持同一套做法。如果未来希望 Service 完全不依赖入站 DTO，
 * 可引入中性的 {@code MenuTreeNode}，由 Converter 映射为 {@code MenuResponse}。
 *
 * @author QI Guang
 */
public interface MenuService {

    // ==================== 查询 ====================

    /**
     * 按 ID 查询菜单，不存在时抛出业务异常。
     *
     * <p>供业务用例使用——菜单不存在说明调用方的假设有误，抛异常让问题
     * 尽早暴露，而不是让调用方拿到 {@code null} 后自己判空、容易遗漏。
     * 供 Facade 层做存在性判断时，应使用不抛异常的 {@link #findById(Long)}。
     *
     * @param id 菜单 ID
     * @return 菜单持久化对象
     * @throws IllegalArgumentException 菜单不存在或已逻辑删除时抛出
     */
    MenuDO getMenu(Long id);

    /**
     * 按 ID 查询菜单，不存在时返回空。
     *
     * <p>与 {@link #getMenu(Long)} 的区别：本方法不抛异常，供
     * {@code MenuFacade} 做存在性判断和查询使用。{@code MenuFacade} 的
     * {@code existsById} 和 {@code findById} 都依赖本方法，前者关心
     * “是否存在”，后者关心“存在时返回什么”。
     *
     * @param id 菜单 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除的菜单；不存在或已逻辑删除时返回空
     */
    Optional<MenuDO> findById(Long id);

    /**
     * 按主键批量查询未删除的菜单。
     *
     * <p>供 {@code MenuFacade.existsByIds} / {@code findByIds} 使用：
     * 批量校验菜单 ID 有效性、批量查询菜单信息。返回顺序不保证与
     * 入参一致——调用方如果依赖顺序，需要自己按入参重排。
     *
     * @param ids 菜单 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除的菜单列表，顺序不保证与入参一致
     */
    List<MenuDO> listByIds(List<Long> ids);

    /**
     * 查询全部菜单（扁平列表，不含树结构）。
     *
     * <p>返回结果按 {@code sortOrder} 升序排列，{@code null} 排末尾。
     * 排序在 Service 层做防御性重排——MySQL 的 {@code ORDER BY}
     * 对 {@code NULL} 的处理是“排最前”，与业务期望的“排末尾”相反，
     * Service 层用 {@code nullsLast} 修正这个差异。
     *
     * <p>树形展示请使用 {@link #listTree(Integer)}。
     *
     * @return 全部菜单的扁平列表；无数据时返回空列表
     */
    List<MenuDO> listAll();

    /**
     * 按菜单类型查询扁平列表，按 {@code sortOrder} 升序排列，{@code null} 排末尾。
     *
     * <p>与 {@link #listAll()} 一样做防御性排序，原因相同。
     *
     * @param menuType 菜单类型：1-目录、2-菜单、3-按钮；{@code null} 时行为同 {@link #listAll()}
     * @return 指定类型的菜单列表；无数据时返回空列表
     */
    List<MenuDO> listByType(Integer menuType);

    /**
     * 查询菜单树（已组装 {@code children}）。
     *
     * <p>树组装在服务层完成：
     * <ul>
     *   <li>按 {@code parentId} 建立父子关系；{@code parentId} 为
     *       {@code null} 或 {@code 0} 的节点作为根节点</li>
     *   <li>每一层按 {@code sortOrder} 升序排列，{@code null} 排末尾</li>
     *   <li>找不到父节点的孤儿节点被丢弃——这通常意味着父节点被
     *       {@code menuType} 过滤掉了；如果业务希望“父节点被过滤时
     *       子节点提升为根”，需要调整本方法的组装策略</li>
     * </ul>
     *
     * <p>返回的 {@link MenuResponse} 是不可变的：子节点列表在构造时
     * 由 {@code List.copyOf} 复制，调用方无法通过修改返回的树影响
     * 后续查询。
     *
     * @param menuType 可选的类型过滤：1-目录、2-菜单、3-按钮；
     *                 {@code null} 返回完整树。角色分配菜单时通常传 {@code 2}，
     *                 只展示可授权的菜单节点
     * @return 已组装的树；无数据时返回空列表
     */
    List<MenuResponse> listTree(Integer menuType);

    // ==================== 命令 ====================

    /**
     * 创建菜单。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>按 {@code menuType} 校验必填字段：目录需 {@code path}，
     *       菜单需 {@code path} + {@code component}，按钮需 {@code permissionCode}</li>
     *   <li>校验 {@code permissionCode} 在未删除菜单中唯一</li>
     *   <li>根据 {@code parentId} 计算 {@code ancestors}：顶级菜单为
     *       {@code "0"}，否则为「父节点 {@code ancestors} + "," + 父节点 id」</li>
     * </ul>
     *
     * <p>{@code ancestors} 由服务端计算，调用方不应在传入的 {@link MenuDO}
     * 中设置该字段，即使设置了也会被覆盖。
     *
     * <p><b>并发下前置校验不可靠：</b>数据库的唯一约束
     * {@code uk_iam_menu_tenant_active_permission_code} 才是最终防线。
     *
     * @param menu 待创建的菜单，{@code ancestors} 由服务端填充
     * @return 新菜单的 ID
     * @throws IllegalArgumentException 类型校验失败、父菜单不存在、
     *                                  权限标识重复时抛出
     */
    Long createMenu(MenuDO menu);

    /**
     * 更新菜单。
     *
     * <p>不修改 {@code parentId} 和 {@code ancestors}：移动节点会触发
     * 整棵子树的 {@code ancestors} 重算，由独立的 move 用例处理，
     * 避免与普通编辑混在一起。即使调用方在 {@code menu} 中传入了
     * 不同的 {@code parentId}，也会被数据库中的原值覆盖。
     *
     * <p>不修改 {@code menuType}：它是结构性属性，更新请求不包含该字段。
     * 实现会用数据库中的原值覆盖传入值，再按类型校验必填字段。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验菜单存在</li>
     *   <li>按 {@code menuType} 校验必填字段（类型取自数据库中的原值）</li>
     *   <li>校验 {@code permissionCode} 唯一性，<b>排除自身</b></li>
     * </ul>
     *
     * @param menu 待更新的菜单，必须包含有效 {@code id}
     * @throws IllegalArgumentException 菜单不存在、类型校验失败、
     *                                  权限标识与其他菜单冲突时抛出
     */
    void updateMenu(MenuDO menu);

    /**
     * 变更菜单状态（启用 / 停用）。
     *
     * <p>状态变更后发布 {@code MenuStatusChangedEvent}，供其他模块
     * （如缓存刷新、审计）响应。状态未实际变化时不发布事件、不写库——
     * 避免订阅方收到“状态从 1 变为 1”这类无意义的通知。
     *
     * @param id     菜单 ID
     * @param status 新状态：0-正常、1-停用
     * @throws IllegalArgumentException 菜单不存在或状态值非法时抛出
     */
    void changeStatus(Long id, Integer status);

    /**
     * 删除菜单。
     *
     * <p>服务端负责：
     * <ul>
     *   <li>校验菜单存在</li>
     *   <li>校验不存在未删除的子菜单——有子菜单时拒绝删除，
     *       避免留下孤儿节点和断裂的 {@code ancestors} 链</li>
     * </ul>
     *
     * <p><b>角色引用的处理：</b>本方法<b>不</b>同步校验菜单是否被角色引用。
     * 那会形成 {@code menu → role} 的反向依赖，与 {@code role → menu::events}
     * 构成循环。角色侧的 {@code iam_role_menu} 关联记录由 {@code role} 模块
     * 监听 {@code MenuDeletedEvent} 异步清理，最终一致。
     *
     * <p><b>并发说明：</b>“检查子菜单”与“删除”之间存在竞态窗口——
     * 并发创建子菜单时可能在本方法检查通过之后、删除提交之前插入
     * 一条 {@code parentId} 指向本菜单的记录，导致孤儿节点。当前实现
     * 未对此加锁；如需强一致，应在数据库层加 {@code parent_id} 外键，
     * 或对本菜单行加 {@code SELECT ... FOR UPDATE}。
     *
     * @param id 菜单 ID
     * @throws IllegalArgumentException 菜单不存在时抛出
     * @throws IllegalStateException    存在子菜单时抛出
     */
    void deleteMenu(Long id);
}
