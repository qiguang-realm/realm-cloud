package cn.realm.cloud.system.server.user.internal.api.web.admin;

import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.user.internal.api.web.admin.converter.UserAdminConverter;
import cn.realm.cloud.system.server.user.internal.api.web.admin.dto.*;
import cn.realm.cloud.system.server.user.internal.application.UserPositionService;
import cn.realm.cloud.system.server.user.internal.application.UserRoleService;
import cn.realm.cloud.system.server.user.internal.application.UserService;
import cn.realm.cloud.system.server.user.internal.persistence.UserDO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户模块管理后台接口入口。
 *
 * <p>面向管理后台的用户管理接口，使用 RBAC 权限模型
 * （如 {@code @PreAuthorize("@ss.hasPermission('system:user:create')")}），
 * 提供用户的分页查询、创建、修改、状态变更、角色分配等管理能力。
 * 与 App 端接口（{@code internal.api.app} 包）在权限模型和请求/响应模型上相互隔离。
 *
 * <p>本类除用户的 CRUD 外，还承载两类关联的分配端点：
 * <ul>
 *   <li>用户-角色：{@code /{userId}/roles}，全量替换用户的角色分配</li>
 *   <li>用户-岗位：{@code /{userId}/positions}，全量替换用户的岗位分配</li>
 * </ul>
 * 这两类关联表（{@code iam_user_role}、{@code iam_user_position}）没有独立于用户的
 * 生命周期，因此不暴露为独立资源，而是作为用户编辑的子能力挂在用户资源下。
 *
 * <p><b>校验职责：</b>{@code @Valid} 触发 DTO 上的格式校验（{@code @NotBlank} /
 * {@code @Min} / {@code @Max}），业务规则校验（如 {@code pageSize} 范围、
 * 用户名唯一性）由 Service 层完成——本类只做 HTTP 协议适配与权限校验，
 * 不做业务判断。
 *
 * <p>本类仅承担 HTTP 协议适配与权限校验，业务编排委托给
 * {@link UserService}、{@link UserRoleService}、{@link UserPositionService}。
 *
 * @author QI Guang
 * @see cn.realm.cloud.system.server.user.internal.api.app.UserAppController
 */
@RestController
@RequestMapping("/admin/iam/user")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;
    private final UserRoleService userRoleService;
    private final UserPositionService userPositionService;

    /* ==================== 用户 CRUD ==================== */

    /**
     * 创建用户。
     *
     * <p>请求中的 {@code password} 是明文密码，Service 层会用
     * {@code PasswordEncoder} 加密后设置到 {@code iam_user.password_hash}。
     * Controller 不接触加密逻辑——加密是 Service 层的职责，与
     * “Controller 只做协议适配”的分层一致。
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:user:create')")
    public Long create(@Valid @RequestBody UserCreateRequest request) {
        return userService.createUser(
                UserAdminConverter.fromCreateRequest(request),
                request.password());
    }

    /**
     * 更新用户。
     *
     * <p>不支持通过本端点修改 {@code username}——它是登录标识，创建后不可变。
     * 更新请求 DTO 中不包含该字段，服务层也会用数据库中的值覆盖，双重保护。
     *
     * <p>同样不修改密码——密码修改走独立的 {@code changePassword} 流程，
     * {@code UserRepositoryImpl.save} 的更新分支不 {@code set passwordHash}。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:user:update')")
    public void update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        UserDO user = userService.getUser(id);
        UserAdminConverter.applyUpdateRequest(request, user);
        userService.updateUser(user);
    }

    /**
     * 查询用户详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public UserResponse get(@PathVariable Long id) {
        return UserAdminConverter.toResponse(userService.getUser(id));
    }

    /**
     * 分页查询用户。
     *
     * <p>{@code UserPageRequest} 为 record，作为 GET 查询参数绑定；
     * {@code @ParameterObject} 让 springdoc 按字段展开文档，而非把整个
     * record 渲染成一个请求体。若项目未引入 springdoc，可去掉该注解，
     * 绑定行为不变，仅文档退化。
     *
     * <p><b>参数传递：</b>{@code request.toPageParam()} 把分页部分剥离为
     * 框架层的 {@code PageParam}，{@code request.username()} /
     * {@code request.status()} 作为查询条件独立传递。Service 不感知
     * HTTP 契约，只接收框架参数 + 业务参数。
     */
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public PageResult<UserResponse> page(@Valid @ParameterObject UserPageRequest request) {
        return UserAdminConverter.toPageResponse(userService.page(
                request.toPageParam(), request.username(), request.status()));
    }

    /**
     * 变更用户状态（启用 / 停用）。
     *
     * <p>用户状态变更后，该用户已签发的登录凭证应失效——
     * 由 {@code UserService.changeStatus} 发布事件，认证模块监听后处理。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermission('system:user:update')")
    public void changeStatus(@PathVariable Long id,
                             @Valid @RequestBody UserStatusUpdateRequest request) {
        userService.changeStatus(id, request.status());
    }

    /**
     * 删除用户。
     *
     * <p>逻辑删除并发布 {@code UserDeletedEvent}，供其他模块清理关联。
     * 用户-角色、用户-岗位关联由 {@code user} 模块内部在同一事务内一并清理，
     * 不依赖跨模块事件。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    public void delete(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    /* ==================== 用户-角色分配 ==================== */

    /**
     * 查询用户已分配的角色 ID 列表。
     *
     * <p>供用户编辑页回显角色勾选状态；前端再结合
     * {@code RoleAdminController.page} 拿到可选角色列表，渲染多选控件。
     */
    @GetMapping("/{userId}/roles")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public List<Long> getRoles(@PathVariable Long userId) {
        return userRoleService.findRoleIdsByUserId(userId);
    }

    /**
     * 全量替换用户的角色分配。
     *
     * <p>语义是“设置用户拥有这些角色”，而不是逐条增删。服务端在写入前通过
     * {@code RoleFacade.existsByIds} 校验 {@code roleIds} 是否全部有效，
     * 避免把无效 ID 写入 {@code iam_user_role}。
     *
     * <p>空列表是合法的——表示清空该用户的所有角色。{@code UserRoleAssignRequest}
     * 的 {@code roleIds} 用 {@code @NotNull} 而非 {@code @NotEmpty}，
     * 正是为了允许“清空”这一语义。
     */
    @PutMapping("/{userId}/roles")
    @PreAuthorize("@ss.hasPermission('system:user:assign-role')")
    public void assignRoles(@PathVariable Long userId,
                            @Valid @RequestBody UserRoleAssignRequest request) {
        userRoleService.replaceUserRoles(userId, request.roleIds());
    }

    /* ==================== 用户-岗位分配 ==================== */

    /**
     * 查询用户已分配的岗位 ID 列表。
     */
    @GetMapping("/{userId}/positions")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public List<Long> getPositions(@PathVariable Long userId) {
        return userPositionService.findPositionIdsByUserId(userId);
    }

    /**
     * 全量替换用户的岗位分配。
     *
     * <p>写入前通过 {@code OrganizationFacade.existsByIds} 校验
     * {@code positionIds} 是否全部有效。空列表表示清空该用户的所有岗位。
     */
    @PutMapping("/{userId}/positions")
    @PreAuthorize("@ss.hasPermission('system:user:assign-position')")
    public void assignPositions(@PathVariable Long userId,
                                @Valid @RequestBody UserPositionAssignRequest request) {
        userPositionService.replaceUserPositions(userId, request.positionIds());
    }
}
