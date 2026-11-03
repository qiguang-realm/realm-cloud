package cn.realm.cloud.system.api.user;

import cn.realm.cloud.framework.common.pojo.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户模块远程调用契约。
 *
 * <p>供其他部署单元通过 Feign 调用。同进程的 Modulith 模块之间应使用
 * {@code UserFacade}，不经过本接口——Feign 会走 HTTP，同进程没有收益。
 *
 * <p><b>路径前缀 {@code /rpc-api}：</b>用于区分“服务间调用”与“管理后台”。
 * {@code /rpc-api/**} 通常只需要服务级鉴权（内部 token 或 mTLS），
 * {@code /admin/**} 需要用户级 RBAC，{@code /app/**} 需要当前用户身份。
 * 三个前缀在 Spring Security 中对应不同的过滤链，不能合并。
 *
 * <p><b>本接口不是 Feign 专用。</b>接口上不标 {@code @FeignClient}——
 * 那是消费方的注解。消费方在自己模块的 {@code internal/client} 下
 * 定义 {@code interface UserFeignClient extends UserApi}，并加
 * {@code @FeignClient(name = "realm-module-system")}。
 *
 * @author QI Guang
 */
@RequestMapping("/rpc-api/user")
public interface UserApi {

    /**
     * 按主键查询用户。
     *
     * <p><b>返回值约定：</b>用户不存在时返回 {@code null}，
     * 不抛 404——远程调用方通常需要自己处理“不存在”的业务逻辑，
     * 抛异常会让调用方被迫捕获 {@code FeignException} 再解析状态码。
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
     * <p>入参为 {@code null} 或空白时返回 {@code false}。
     *
     * @param username 用户名
     * @return 存在时为 {@code true}
     */
    @GetMapping("/exists")
    boolean existsByUsername(@RequestParam("username") String username);

    /**
     * 分页查询用户。
     *
     * <p><b>为什么用 POST：</b>GET 携带 body 在 HTTP 规范中语义未定义，
     * 部分代理和 HTTP 客户端会丢弃 body。分页查询参数较多，
     * 用 POST + body 是标准做法，避免参数拼接和编码问题。
     *
     * @param request 分页查询条件
     * @return 分页结果；无匹配时返回 {@code total=0, list=[]}，不返回 {@code null}
     */
    @PostMapping("/page")
    PageResult<UserResponse> page(@RequestBody UserPageRequest request);
}
