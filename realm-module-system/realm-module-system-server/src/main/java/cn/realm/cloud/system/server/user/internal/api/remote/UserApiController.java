package cn.realm.cloud.system.server.user.internal.api.remote;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.api.user.UserApi;
import cn.realm.cloud.system.api.user.UserPageRequest;
import cn.realm.cloud.system.api.user.UserResponse;
import cn.realm.cloud.system.server.user.internal.application.UserService;
import cn.realm.cloud.system.server.user.internal.persistence.UserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@link UserApi} 的服务端实现。
 *
 * <p>远程调用端点，路径由接口上的 {@code @RequestMapping("/rpc-api/user")} 决定。
 * 与管理后台的 {@code /admin/iam/user}、App 端的 {@code /app/iam/user}
 * 是三个独立的入口，权限模型不同。
 *
 * <p><b>认证模型：</b>RPC 端点不使用 {@code @PreAuthorize}——它面向的是
 * 其他模块/服务的调用，不是终端用户。当前依赖 {@code SecurityFilterChain}
 * 对 {@code /rpc-api/**} 的放行 + 内网/防火墙保护。如果未来拆分微服务，
 * 应改为服务间认证（OAuth2 client_credentials 或 mTLS），而不是让
 * RPC 调用方携带用户 token——那会把“服务间调用”和“用户身份”混在一起，
 * 语义错位。
 *
 * <p><b>契约隔离：</b>RPC 契约的 {@code UserResponse} 与管理后台的
 * {@code UserResponse} 是**两个不同的类**（分属 {@code system-api} 和
 * {@code system-server} 模块）。RPC 契约只返回其他模块真正需要的字段
 * （{@code id} / {@code username} / {@code nickname} / {@code departmentId}
 * / {@code status}），不暴露手机号、邮箱、头像等管理端字段——远程调用
 * 的消费方不需要这些，暴露它们只会扩大敏感信息的传播面。
 *
 * @author QI Guang
 */
@RestController
@RequiredArgsConstructor
public class UserApiController implements UserApi {

    private final UserService userService;

    @Override
    public UserResponse getById(Long id) {
        return userService.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    public List<UserResponse> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userService.listByIds(ids).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public boolean existsByUsername(String username) {
        return userService.existsByUsername(username);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>分页参数的来源：</b>{@code UserPageRequest} 是 {@code system-api}
     * 模块的 RPC 契约，字段是 class + Lombok getter（不是 record）。
     * 本方法把它的 {@code pageNo} / {@code pageSize} 转成框架层的
     * {@link PageParam}，传给 {@code UserService.page}——Service 层
     * 不感知 RPC 契约。
     *
     * <p><b>校验职责：</b>{@code pageSize} 的合法性由 Service 层最终
     * 保证（{@code 1 ~ PageParam.MAX_PAGE_SIZE}）。本方法额外校验
     * {@code pageSize <= 0}，是为了给 RPC 调用方更明确的错误信息
     * （“远程分页查询不允许 pageSize <= 0” vs Service 层的
     * “pageSize 必须在 1 到 100 之间”）。两层校验不冲突，错误信息
     * 各有侧重——RPC 层提示“远程调用不允许”，Service 层提示“范围”。
     *
     * <p><b>为什么不复用 {@code UserPageRequest} 的 {@code toPageParam()}：</b>
     * RPC 契约的 {@code UserPageRequest} 位于 {@code system-api} 模块，
     * 不能依赖 {@code realm-core} 的 {@code PageParam}——那会让 api
     * 模块依赖框架层，破坏“api 模块只定义契约、不依赖实现”的分层。
     * 所以转换逻辑放在服务端的 Controller 中。
     */
    @Override
    public PageResult<UserResponse> page(UserPageRequest request) {
        // RPC 契约的 UserPageRequest 是 class，用 getter 访问
        Integer pageNo = request.getPageNo();
        Integer pageSize = request.getPageSize();

        // 兜底：RPC 调用方绕过 Bean Validation 直接传 null 时，用默认值
        int effectivePageNo = pageNo != null ? pageNo : PageParam.DEFAULT_PAGE_NO;
        int effectivePageSize = pageSize != null ? pageSize : PageParam.DEFAULT_PAGE_SIZE;

        // RPC 层校验下限：给远程调用方明确的错误信息
        if (effectivePageSize <= 0) {
            throw new IllegalArgumentException(
                    "远程分页查询不允许 pageSize <= 0，当前: " + effectivePageSize);
        }

        // 转成框架层的 PageParam，调用 Service 的三参数版本
        PageParam pageParam = PageParam.of(effectivePageNo, effectivePageSize);
        PageResult<UserDO> source = userService.page(
                pageParam, request.getUsername(), request.getStatus());

        List<UserResponse> list = source.getList().stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(list, source.getTotal(),
                source.getPageSize(), source.getPageNo());
    }

    /**
     * {@code UserDO} → 远程契约 {@code UserResponse}。
     *
     * <p>只映射 RPC 消费方需要的字段，不包含手机号、邮箱、头像——
     * 远程调用的语义是“拿用户的基本标识和归属”，不是“展示用户详情”。
     * 需要更多字段的调用方，应该在自己的模块里通过 {@code UserFacade}
     * 或扩展 RPC 契约来满足，而不是让 {@code UserResponse} 膨胀成
     * 管理端的镜像。
     */
    private UserResponse toResponse(UserDO user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getDepartmentId(),
                user.getStatus()
        );
    }
}
