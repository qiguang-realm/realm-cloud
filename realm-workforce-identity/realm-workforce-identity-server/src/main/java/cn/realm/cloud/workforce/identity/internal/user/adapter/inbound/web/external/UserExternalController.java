package cn.realm.cloud.workforce.identity.internal.user.adapter.inbound.web.external;

import cn.realm.cloud.workforce.identity.internal.user.adapter.inbound.web.admin.UserAdminController;
import cn.realm.cloud.workforce.identity.internal.user.application.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放平台用户资源入口，与 {@link UserExternalApi} 为配套HTTP契约。
 *
 * <p>面向第三方合作系统、开放平台的用户资源视图，供外部授权合作方查询、同步用户基础信息。
 * 与 {@link UserAdminController}、{@link UserAppController}、{@link UserInternalController} 接入语境、鉴权模型、数据范围互相独立。
 *
 * <p><b>访问控制设计：</b>
 * 采用开放平台凭证鉴权（AK/SK 或开放 OAuth2 应用凭证），不属于终端用户会话，也不使用后台RBAC权限点。
 * 所有请求绑定第三方应用身份，接口具备应用级的数据范围隔离，按预分配的授权范围返回数据；
 * 外部合作方无法随意遍历、跨租户读取全部用户数据。
 *
 * <p><b>视图契约隔离（AIP-154 数据最小化）：</b>
 * 使用独立DTO，仅对外透出合作业务必需的公开/授权字段；
 * 屏蔽内部管理字段、敏感个人信息（原始手机号、邮箱、角色、岗位、账号状态等），严格控制对外暴露面。
 *
 * <p><b>分层职责边界：</b>本Controller属于inbound适配器，仅承担HTTP协议适配、OpenAPI契约声明；
 * 业务编排、授权范围校验委托给 {@link UserService}，不承载领域业务逻辑。
 *
 * @author QI Guang
 * @see UserAdminController
 * @see UserAppController
 * @see UserInternalController
 * @see UserExternalApi
 * @see <a href="https://google.aip.dev/154">AIP-154 Data hiding</a>
 */
@RestController
@RequestMapping("/identity/external/users")
@Tag(name = "Identity-External", description = "身份域｜开放平台第三方接口")
@RequiredArgsConstructor
public class UserExternalController {

    private final UserService userService;
}



