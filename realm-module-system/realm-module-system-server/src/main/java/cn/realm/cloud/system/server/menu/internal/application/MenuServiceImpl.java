package cn.realm.cloud.system.server.menu.internal.application;

import cn.realm.cloud.system.server.menu.events.MenuDeletedEvent;
import cn.realm.cloud.system.server.menu.events.MenuStatusChangedEvent;
import cn.realm.cloud.system.server.menu.internal.api.web.admin.converter.MenuAdminConverter;
import cn.realm.cloud.system.server.menu.internal.api.web.admin.dto.MenuResponse;
import cn.realm.cloud.system.server.menu.internal.persistence.MenuDO;
import cn.realm.cloud.system.server.menu.internal.persistence.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link MenuService} 的默认实现。
 *
 * <p>负责 {@code ancestors} 的拼接、删除前的子节点校验、按菜单类型的
 * 必填字段校验、权限标识唯一性校验。数据访问委托给 {@link MenuRepository}，
 * 不直接依赖 MyBatis-Plus。
 *
 * <p><b>删除时的角色引用处理：</b>本模块<b>不</b>同步校验菜单是否被角色引用——
 * 那会形成 {@code menu → role} 的反向依赖，与 {@code role → menu::events}
 * 构成循环。角色侧的 {@code iam_role_menu} 关联记录由 {@code role} 模块
 * 监听 {@link MenuDeletedEvent} 异步清理，最终一致。在清理完成之前，
 * role 侧可能短暂持有指向已删除菜单的 ID，订阅方查询时应容忍查不到
 * 对应菜单，清理逻辑须幂等。
 *
 * <p><b>关于 {@code ancestors}：</b>它是服务端子树查询用的辅助列，
 * 格式为逗号分隔的祖先 ID 链，顶级菜单为 {@code "0"}。
 * 该字段永远由本类计算，不接受外部输入。
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class MenuServiceImpl implements MenuService {

    /**
     * 顶级菜单的固定祖先链前缀。
     *
     * <p>格式为逗号分隔的祖先 ID 链，顶级菜单没有祖先，
     * 用 {@code "0"} 作为哨兵值，使子孙查询 {@code LIKE '0,%'} 能覆盖
     * 所有非顶级菜单。
     */
    private static final String ROOT_ANCESTORS = "0";

    /**
     * 顶级菜单的父节点 ID。
     *
     * <p>{@code parentId} 为 {@code null} 或 {@code 0} 时都按顶级处理。
     * 数据库 DDL 中顶级菜单的 {@code parent_id} 为 {@code 0}，
     * 这里用常量与 DDL 保持一致。
     */
    private static final long ROOT_PARENT_ID = 0L;

    /**
     * 状态值：正常
     */
    private static final int STATUS_NORMAL = 0;

    /**
     * 状态值：停用
     */
    private static final int STATUS_DISABLED = 1;

    private final MenuRepository menuRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 查询 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public MenuDO getMenu(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在: " + id));
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code id} 为 {@code null} 时直接返回空，避免把 {@code null}
     * 传给仓储层触发无意义的查询。虽然 {@code MenuFacadeImpl} 的入口已经
     * 判过 {@code null}，但 {@code MenuService} 是 {@code internal} 包的
     * 公开接口，其他内部组件也可能直接调用，这里再判一次成本极低。
     */
    @Override
    public Optional<MenuDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return menuRepository.findById(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>空集合直接返回空列表，不查库——{@code IN ()} 是非法 SQL，
     * 即使 MP 会处理成“永不匹配”的条件，多一次无意义的数据库往返也没必要。
     */
    @Override
    public List<MenuDO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return menuRepository.findByIds(ids);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>为什么在 Service 层重排：</b>Repository 的 {@code findAll}
     * 已按 {@code sort_order}、{@code id} 升序排列，但 MySQL 的
     * {@code ORDER BY} 对 {@code NULL} 的处理是“排最前”，与业务期望的
     * “排末尾”相反。Service 层用 {@code Comparator.nullsLast} 修正这个差异，
     * 把“排序语义”固定在应用层，不依赖数据库方言的具体行为。
     */
    @Override
    public List<MenuDO> listAll() {
        return menuRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        MenuDO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>与 {@link #listAll()} 一样做防御性排序，原因相同。
     */
    @Override
    public List<MenuDO> listByType(Integer menuType) {
        return menuRepository.findByType(menuType).stream()
                .sorted(Comparator.comparing(
                        MenuDO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>组装步骤：</b>
     * <ol>
     *   <li>按 {@code menuType} 过滤（{@code null} 时取全部）</li>
     *   <li>按 {@code parentId} 分组；{@code null} 归一为 {@link #ROOT_PARENT_ID}。
     *       使用 {@link LinkedHashMap} 保持分组后的顺序与输入一致</li>
     *   <li>从根节点开始递归构造 {@link MenuResponse}——自底向上，
     *       先递归得到子节点列表，再构造父节点，因为 {@code MenuResponse}
     *       是不可变 {@code record}，不能“先建父节点再往里塞子节点”</li>
     * </ol>
     *
     * <p><b>孤儿节点处理：</b>{@code parentId} 指向不在当前结果集中的
     * 节点会被丢弃——通常意味着父节点被 {@code menuType} 过滤掉了。
     * 丢弃而不是挂到根上，避免数据结构错误被掩盖。
     */
    @Override
    public List<MenuResponse> listTree(Integer menuType) {
        List<MenuDO> all = (menuType == null) ? listAll() : listByType(menuType);
        if (all.isEmpty()) {
            return List.of();
        }
        // 按 parentId 分组，null 归一为 ROOT_PARENT_ID。
        // LinkedHashMap 保持分组后的顺序与 listAll() / listByType() 返回的顺序一致。
        Map<Long, List<MenuDO>> byParent = all.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getParentId() == null ? ROOT_PARENT_ID : m.getParentId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return buildChildren(ROOT_PARENT_ID, byParent);
    }

    // ==================== 命令 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>校验顺序：</b>
     * <ol>
     *   <li>按 {@code menuType} 校验必填字段（最轻的本地判断）</li>
     *   <li>校验 {@code permissionCode} 唯一性（一次数据库查询）</li>
     *   <li>计算 {@code ancestors}——父菜单不存在时抛异常</li>
     * </ol>
     * 先校验“轻”的，尽早失败，不浪费数据库查询。
     */
    @Override
    @Transactional
    public Long createMenu(MenuDO menu) {
        validateByType(menu);
        validatePermissionCodeUnique(menu.getPermissionCode(), null);

        // ancestors 由服务端计算，覆盖调用方传入的任何值
        menu.setAncestors(resolveAncestors(menu.getParentId()));

        menuRepository.save(menu);
        log.info("创建菜单成功, id={}, name={}, type={}",
                menu.getId(), menu.getMenuName(), menu.getMenuType());
        return menu.getId();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>前提：</b>调用方传入的 {@code menu} 通常是“先 {@code getMenu}
     * 拿到完整对象、再由 Converter 应用更新字段”的结果。本方法会用数据库中的
     * 原值覆盖 {@code menuType}、{@code parentId}、{@code ancestors}，
     * 因此即使调用方只传了部分字段，这三个字段也不会被误改。
     *
     * <p><b>校验顺序：</b>
     * <ol>
     *   <li>菜单存在</li>
     *   <li>用数据库中的 {@code menuType} 覆盖后再按类型校验必填字段——
     *       更新请求不含 {@code menuType}，不能依赖调用方传入</li>
     *   <li>{@code permissionCode} 唯一性，排除自身</li>
     * </ol>
     */
    @Override
    @Transactional
    public void updateMenu(MenuDO menu) {
        MenuDO existing = getMenu(menu.getId());

        // menuType 是结构性属性，更新请求不包含它；用数据库中的值覆盖后再校验，
        // 避免调用方传入 null 导致 validateByType 误报“菜单类型不能为空”
        menu.setMenuType(existing.getMenuType());
        validateByType(menu);

        // 权限标识唯一性：排除自身，避免把未改动的当前值判为冲突
        validatePermissionCodeUnique(menu.getPermissionCode(), menu.getId());

        // 不允许改 parentId / ancestors —— 移动节点会触发整棵子树 ancestors 重算，
        // 由独立的 moveMenu 处理，避免与普通编辑混在一起。
        // 用数据库原值覆盖，即使调用方误传也不会生效；
        // Repository 层的 save 也不 set 这两个字段，是第二重保护。
        menu.setParentId(existing.getParentId());
        menu.setAncestors(existing.getAncestors());

        menuRepository.save(menu);
        log.info("更新菜单成功, id={}, name={}", menu.getId(), menu.getMenuName());
    }

    /**
     * {@inheritDoc}
     *
     * <p>状态未实际变化时不写库、不发布事件，避免订阅方收到
     * “状态从 1 变为 1” 这类无意义的通知。
     *
     * <p><b>关于 {@code status == null} 判断的顺序：</b>
     * {@code status != STATUS_NORMAL} 中 {@code status} 是 {@code Integer}、
     * {@code STATUS_NORMAL} 是 {@code int}——会拆箱。{@code status == null}
     * 的短路必须放在前面，否则拆箱 NPE。顺序不能颠倒。
     */
    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        // 顺序不能颠倒：status == null 必须在前面，避免后续拆箱 NPE
        if (status == null || (status != STATUS_NORMAL && status != STATUS_DISABLED)) {
            throw new IllegalArgumentException("非法的状态值: " + status);
        }
        MenuDO menu = getMenu(id);
        // 状态未变化时短路，不写库、不发事件
        if (Objects.equals(menu.getStatus(), status)) {
            return;
        }
        menu.setStatus(status);
        menuRepository.save(menu);
        eventPublisher.publishEvent(new MenuStatusChangedEvent(id, status));
        log.info("变更菜单状态, id={}, status={}", id, status);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>执行顺序：</b>
     * <ol>
     *   <li>校验菜单存在</li>
     *   <li>校验不存在未删除的子菜单——有子菜单时拒绝删除，
     *       避免留下孤儿节点和断裂的 {@code ancestors} 链</li>
     *   <li>逻辑删除菜单本身</li>
     *   <li>发布 {@code MenuDeletedEvent}——供 {@code role} 模块异步清理
     *       角色对菜单的授权</li>
     * </ol>
     * 前三步在同一事务内，任一步失败都会整体回滚。第 4 步的事件在
     * 事务内发布，但订阅方用 {@code @ApplicationModuleListener} 消费，
     * 实际执行在事务提交之后。
     *
     * <p><b>并发说明：</b>“检查子菜单”与“删除”之间存在竞态窗口——
     * 并发创建子菜单时可能在本方法检查通过之后、删除提交之前插入
     * 一条 {@code parentId} 指向本菜单的记录，导致孤儿节点。当前实现
     * 未对此加锁；如需强一致，应在数据库层加 {@code parent_id} 外键，
     * 或对本菜单行加 {@code SELECT ... FOR UPDATE}。
     */
    @Override
    @Transactional
    public void deleteMenu(Long id) {
        getMenu(id);
        // 子菜单存在性校验：有子菜单时拒绝删除
        if (menuRepository.existsByParentId(id)) {
            throw new IllegalStateException("存在子菜单，无法删除: " + id);
        }
        menuRepository.deleteById(id);
        // 跨模块关联（iam_role_menu）由 role 模块监听事件异步清理
        eventPublisher.publishEvent(new MenuDeletedEvent(id));
        log.info("删除菜单成功, id={}", id);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 计算祖先链。
     *
     * <p><b>规则：</b>顶级菜单为 {@link #ROOT_ANCESTORS}（{@code "0"}）；
     * 非顶级为「父节点 {@code ancestors} + "," + 父节点 id」。
     *
     * <p><b>空值防御：</b>父节点的 {@code ancestors} 为 {@code null} 或空白时
     * （历史数据、手工插入、迁移遗漏）按根处理，避免拼出 {@code "null,5"}
     * 这类脏数据——这类数据会让后续 {@code ancestors LIKE '0,5%'} 的
     * 子树查询完全失效，且难以追溯。
     *
     * <p><b>未来 move 操作的隐患：</b>本方法只在创建时调用，新菜单还没有
     * id，不会把 {@code parentId} 指向自己。但未来实现 {@code moveMenu}
     * 时，必须加校验“不能把节点移动到自己或自己的子孙下”——否则
     * {@code ancestors} 链会形成环，子树查询会死循环。
     *
     * @param parentId 父菜单 ID；{@code null} 或 {@code 0} 表示顶级
     * @return 逗号分隔的祖先链
     * @throws IllegalArgumentException 父菜单不存在时抛出
     */
    private String resolveAncestors(Long parentId) {
        // 顶级菜单：直接返回 "0"
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return ROOT_ANCESTORS;
        }
        MenuDO parent = menuRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("父级菜单不存在: " + parentId));
        String parentAncestors = parent.getAncestors();
        // 父菜单 ancestors 为空时按根处理，避免拼出 "null,5"
        if (parentAncestors == null || parentAncestors.isBlank()) {
            parentAncestors = ROOT_ANCESTORS;
        }
        return parentAncestors + "," + parent.getId();
    }

    /**
     * 校验权限标识唯一性。
     *
     * <p>{@code permissionCode} 为 {@code null} 或空白时跳过校验——
     * 目录和菜单允许没有权限标识，只有按钮要求必填（由 {@link #validateByType}
     * 负责）。{@code excludeId} 非空时排除该 ID 自身，供更新场景使用。
     *
     * <p><b>并发下前置校验不可靠：</b>数据库的唯一约束
     * {@code uk_iam_menu_tenant_active_permission_code} 才是最终防线。
     *
     * @param permissionCode 待校验的权限标识
     * @param excludeId      排除的菜单 ID；{@code null} 表示不排除（创建场景）
     * @throws IllegalArgumentException 权限标识已被其他菜单占用时抛出
     */
    private void validatePermissionCodeUnique(String permissionCode, Long excludeId) {
        if (isBlank(permissionCode)) {
            return;
        }
        boolean exists = (excludeId == null)
                ? menuRepository.existsByPermissionCode(permissionCode)
                : menuRepository.existsByPermissionCodeAndIdNot(permissionCode, excludeId);
        if (exists) {
            throw new IllegalArgumentException("权限标识已存在: " + permissionCode);
        }
    }

    /**
     * 按菜单类型校验必填字段：
     * <ul>
     *   <li>1-目录：需 {@code path}</li>
     *   <li>2-菜单：需 {@code path} + {@code component}</li>
     *   <li>3-按钮：需 {@code permissionCode}</li>
     * </ul>
     *
     * <p>这些是跨字段的条件约束，无法用 Jakarta Validation 的单字段注解表达，
     * 因此在服务层校验。错误信息使用业务语言，便于前端直接展示。
     */
    private void validateByType(MenuDO menu) {
        Integer type = menu.getMenuType();
        if (type == null) {
            throw new IllegalArgumentException("菜单类型不能为空");
        }
        switch (type) {
            case 1 -> {
                if (isBlank(menu.getPath())) {
                    throw new IllegalArgumentException("目录必须填写路由地址");
                }
            }
            case 2 -> {
                if (isBlank(menu.getPath()) || isBlank(menu.getComponent())) {
                    throw new IllegalArgumentException("菜单必须填写路由地址和组件路径");
                }
            }
            case 3 -> {
                if (isBlank(menu.getPermissionCode())) {
                    throw new IllegalArgumentException("按钮必须填写权限标识");
                }
            }
            default -> throw new IllegalArgumentException("非法的菜单类型: " + type);
        }
    }

    /**
     * 递归构造子树。
     *
     * <p><b>自底向上构造：</b>先递归得到子节点的 {@code MenuResponse} 列表，
     * 再构造当前节点的 {@code MenuResponse} 并把子节点列表传入。这是因为
     * {@link MenuResponse} 是不可变 {@code record}，不能“先建父节点
     * 再往里塞子节点”。
     *
     * <p><b>排序说明：</b>{@code byParent} 来自 {@link #listAll()} /
     * {@link #listByType(Integer)}（已排序），且用 {@code LinkedHashMap}
     * 保序，所以本方法中的 {@code sorted} 是防御性冗余。保留它是为了
     * 未来 {@code listAll} / {@code listByType} 或分组逻辑变更时不破坏排序。
     *
     * @param parentId 当前层的父节点 ID
     * @param byParent 按 {@code parentId} 分组的菜单索引
     * @return 当前层的 {@code MenuResponse} 列表，按 {@code sortOrder} 升序
     */
    private List<MenuResponse> buildChildren(Long parentId,
                                             Map<Long, List<MenuDO>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                // 防御性排序：输入已排序，保留此排序以防上游变更
                .sorted(Comparator.comparing(
                        MenuDO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(child -> MenuAdminConverter.toResponse(
                        child, buildChildren(child.getId(), byParent)))
                .toList();
    }

    /**
     * 判断字符串是否为 {@code null} 或空白。
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
