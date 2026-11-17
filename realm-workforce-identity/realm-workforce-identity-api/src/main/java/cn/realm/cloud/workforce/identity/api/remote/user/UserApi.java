package cn.realm.cloud.workforce.identity.api.remote.user;

import cn.realm.cloud.framework.core.dto.PageResult;
import cn.realm.cloud.workforce.identity.api.remote.user.dto.UserPageRequest;
import cn.realm.cloud.workforce.identity.api.remote.user.dto.UserResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户模块服务间调用契约。
 *
 * <p><b>适用场景：</b>
 * 供其他部署单元（未来的微服务或外部系统）通过 HTTP 客户端调用。
 * <b>同进程的 Spring Modulith 模块之间应使用 {@code IdentityFacade}</b>，
 * 不经过本接口——同进程方法调用没有 HTTP 序列化和网络开销。
 *
 * <p><b>路径前缀 {@code /internal}：</b>
 * 用于区分“服务间内部调用”与 {@code /admin}（管理端）、{@code /app}（C端）。
 * 依据国际主流 API 设计规范，URI 应面向资源与调用者语境，而不是绑定具体技术协议。
 * 三类前缀在网关或 Spring Security 中对应不同的鉴权策略：
 * <ul>
 *     <li>{@code /internal/**}：服务级鉴权（内部 Token / mTLS / 网络隔离）</li>
 *     <li>{@code /admin/**}：用户级 RBAC 权限校验</li>
 *     <li>{@code /app/**}：终端用户身份校验（BOLA 防御）</li>
 * </ul>
 *
 * <p><b>契约独立性：</b>
 * 本接口不标 {@code @FeignClient}，不绑定任何具体的调用框架。
 * 消费方（如 {@code realm-analytics}）应在自己的 {@code internal/client} 下
 * 定义 {@code interface UserFeignClient extends UserApi}，并添加
 * {@code @FeignClient(name = "realm-workforce-identity-server")}。
 *
 * @author QI Guang
 */
@RequestMapping("/internal/user")
public interface UserApi {

    /**
     * 按主键查询用户。
     *
     * <p><b>返回值约定：</b>用户不存在时返回 {@code null}，不抛 404。
     * 远程调用方通常需要根据“是否存在”执行不同的业务分支（如降级、补偿），
     * 抛出 404 异常会迫使调用方捕获 {@code FeignException} 并解析 HTTP 状态码，
     * 增加了不必要的耦合。
     *
     * @param id 用户 ID
     * @return 用户信息；不存在时返回 {@code null}
     */
    @GetMapping("/{id}")
    UserResponse getById(@PathVariable("id") Long id);

    /**
     * 按主键批量查询用户。
     *
     * <p><b>返回值约定：</b>入参为 {@code null} 或空列表时返回空列表；
     * 部分 ID 不存在时只返回存在的记录，不报错。
     * 返回顺序不保证与入参一致，调用方如需顺序请自行排序。
     *
     * @param ids 用户 ID 列表
     * @return 用户信息列表；无匹配时返回空列表，不返回 {@code null}
     */
    @PostMapping("/batch")
    List<UserResponse> listByIds(@RequestBody List<Long> ids);

    /**
     * 判断用户名是否已被占用。
     *
     * <p>入参为 {@code null} 或空白时返回 {@code false}，避免非法参数导致服务端异常。
     *
     * @param username 用户名
     * @return 存在时为 {@code true}
     */
    @GetMapping("/exists")
    boolean existsByUsername(@RequestParam("username") String username);

    /**
     * 分页查询用户。
     *
     * <p><b>为什么用 POST 而不是 GET：</b>
     * HTTP 规范（RFC 9110）中未定义 GET 请求携带 Body 的语义，部分代理、
     * 网关和底层 HTTP 客户端（如 Nginx、OkHttp）可能会静默丢弃 Body。
     * 分页查询参数较多，用 POST + JSON Body 是行业标准做法，
     * 既能避免 URL 长度限制，又能避免复杂的参数拼接与编码问题。
     *
     * @param request 分页查询条件
     * @return 分页结果；无匹配时返回 {@code total=0, list=[]}，不返回 {@code null}
     */
    @PostMapping("/page")
    PageResult<UserResponse> page(@RequestBody UserPageRequest request);
}
