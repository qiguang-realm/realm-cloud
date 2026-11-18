package cn.realm.cloud.workforce.identity.internal.user.adapter.inbound.internal;

import cn.realm.cloud.framework.core.dto.PageParam;
import cn.realm.cloud.framework.core.dto.PageResult;
import cn.realm.cloud.workforce.identity.api.remote.user.UserInternalApi;
import cn.realm.cloud.workforce.identity.api.remote.user.dto.UserPageRequest;
import cn.realm.cloud.workforce.identity.api.remote.user.dto.UserResponse;
import cn.realm.cloud.workforce.identity.internal.user.application.UserService;
import cn.realm.cloud.workforce.identity.internal.user.infrastructure.persistence.UserDO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@link UserInternalApi} 的服务端实现。
 *
 * <p>跨部署单元远程 HTTP 端点，路由契约由接口 {@link UserInternalApi} 上的 {@code @RequestMapping("/identity/internal/users")} 定义。
 * 与管理后台视图 {@code /identity/admin/users}、C端App视图 {@code /identity/app/users}、开放平台视图 {@code /identity/external/users}
 * 为互相独立的接入入口，采用隔离的权限模型、独立DTO契约。
 *
 * <p><b>认证模型：</b>本内部视图面向其他服务/部署单元调用，非终端用户入口，不使用用户维度 {@code @PreAuthorize} RBAC校验。
 * 当前由 {@code SecurityFilterChain} 统一管控 {@code /identity/internal/**} 路径；长期演进方案采用服务级身份认证：OAuth2 Client Credentials 或 mTLS。
 * 禁止消费方传递终端用户Token来完成服务间调用，避免服务身份与用户身份语义混淆、权限模型污染。
 *
 * <p><b>契约与数据最小化原则（AIP-154）：</b>
 * 本视图使用独立的DTO集合，与 admin/app/external 视图的DTO严格隔离。
 * RPC契约 {@code UserResponse} 仅透出消费方业务必需字段（id / username / nickname / departmentId / status），
 * 不向外暴露手机号、邮箱等敏感字段；遵循最小必要暴露原则，缩减敏感数据攻击面。
 *
 * <p><b>分层职责边界：</b>
 * 本 Controller 属于 inbound 适配器，仅承担 HTTP 协议适配、请求转发、契约绑定；
 * 业务逻辑编排、领域规则校验下沉至 application / domain 层。
 *
 * @author QI Guang
 * @see UserInternalApi
 * @see <a href="https://google.aip.dev/154">AIP-154 Data hiding</a>
 */
@RestController
@RequestMapping("/identity/internal/users")
@Tag(name = "Identity-Internal", description = "身份域｜微服务跨部署单元内部调用接口，禁止公网访问")
@RequiredArgsConstructor
public class UserInternalController implements UserInternalApi {

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
