package cn.realm.cloud.workforce.identity.api.remote.user;

import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 用户资源｜开放平台第三方调用契约。
 *
 * <p><b>适用场景：</b>
 * 供外部第三方合作系统通过 HTTP 客户端调用。
 * <b>同进程 Spring Modulith 模块之间优先使用 {@code IdentityFacade}</b>，
 * 不经过本 HTTP 契约——同进程方法调用无 HTTP 序列化、网络开销。
 *
 * <p><b>路径语义说明：{@code /identity/external/users}</b>
 * {@code identity} = 身份域；{@code external} = 开放平台第三方调用视图；
 * 用于区分四类接入语境：
 * <ul>
 *     <li>{@code /identity/internal/**}：跨服务内部调用，服务级鉴权（内部Token / mTLS / 内网网络隔离）</li>
 *     <li>{@code /identity/admin/**}：B端管理后台，用户级 RBAC 权限校验</li>
 *     <li>{@code /identity/app/**}：C端App/小程序，终端用户身份校验（BOLA 防御）</li>
 *     <li>{@code /identity/external/**}：开放平台第三方合作伙伴，AK/SK 或开放 OAuth2 应用凭证鉴权，带应用数据范围隔离</li>
 * </ul>
 * 遵循 Google AIP API 规范：URI 表达【域 → 调用语境(视图) → 资源】，不绑定底层技术协议。
 *
 * <p><b>契约独立性约束：</b>
 * 本接口仅为 HTTP API 契约，不标注 {@code @FeignClient}，不绑定任何调用框架。
 * 消费方在自身 {@code internal/client} 包下
 * 定义 {@code interface UserExternalFeignClient extends UserExternalApi}，自行增加
 * {@code @FeignClient(name = "realm-workforce-identity-server")}。
 *
 * @author QI Guang
 */
@RequestMapping("/identity/external/users")
public interface UserExternalApi {

}
