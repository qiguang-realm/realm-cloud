package cn.realm.cloud.workforce.identity.internal.user.adapter.inbound.web.app;

import cn.realm.cloud.workforce.identity.internal.user.adapter.inbound.web.admin.UserAdminController;
import cn.realm.cloud.workforce.identity.internal.user.adapter.inbound.web.app.converter.UserAppConverter;
import cn.realm.cloud.workforce.identity.internal.user.adapter.inbound.web.app.dto.UserProfileResponse;
import cn.realm.cloud.workforce.identity.internal.user.adapter.inbound.web.app.dto.UserProfileUpdateRequest;
import cn.realm.cloud.workforce.identity.internal.user.application.UserService;
import cn.realm.cloud.workforce.identity.internal.user.infrastructure.persistence.UserDO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * {@link UserAppApi} 的服务端实现。
 *
 * <p>面向移动App、小程序等C端消费者的用户资源视图，仅允许当前登录用户操作自身资料（查询、修改个人档案），
 * <b>不具备跨用户管理能力</b>。这是和 {@link UserAdminController} 的核心差异：管理端可通过路径参数指定任意用户，
 * App端操作主体固定为安全上下文中的登录身份。
 *
 * <p><b>防越权设计（BOLA 防御）：</b>
 * 接口不对外接收 {@code userId} 参数，用户标识直接从安全上下文 {@code authentication.principal} 获取。
 * 该方案从接口契约层面消除传入他人用户ID的入口，规避对象级越权风险；
 * 不需要额外编写“入参userId == 当前登录用户”的校验逻辑，把安全约束固化在API结构上。
 *
 * <p><b>视图契约隔离：</b>与管理后台、内部服务调用、开放平台视图在两套维度严格隔离：
 * <ul>
 *   <li><b>权限模型隔离：</b>App端不使用RBAC权限点 {@code @PreAuthorize("@ss.hasPermission(...)")}，
 *       校验前提仅为有效终端用户身份已认证；</li>
 *   <li><b>DTO模型隔离（AIP-154 数据最小化）：</b>使用独立的 {@code UserProfileResponse}，
 *       仅返回C端展示必需字段（昵称、头像、脱敏手机号），不透出部门、角色、账号状态、原始邮箱等管理类敏感字段。</li>
 * </ul>
 *
 * <p><b>分层职责边界：</b>本Controller属于inbound适配器，仅承担HTTP协议适配、OpenAPI契约声明；
 * 业务编排与业务规则校验委托给 {@link UserService}，不承载领域业务逻辑。
 *
 * @author QI Guang
 * @see UserAdminController
 * @see UserAppApi
 * @see <a href="https://google.aip.dev/154">AIP-154 Data hiding</a>
 */
@RestController
@RequestMapping("/identity/app/users")
@Tag(name = "Identity-App", description = "身份域｜C端App接口")
@RequiredArgsConstructor
public class UserAppController {

    private final UserService userService;

    /**
     * 查询当前登录用户的个人资料。
     *
     * <p>返回昵称、头像、脱敏手机号，不包含部门、角色、状态等管理字段。
     */
    @GetMapping("/profile")
    public UserProfileResponse getProfile(@AuthenticationPrincipal Long userId) {
        return UserAppConverter.toProfileResponse(userService.getUser(userId));
    }

    /**
     * 更新当前登录用户的个人资料。
     *
     * <p>只允许修改昵称和头像——手机号、邮箱等敏感字段的变更需要
     * 独立的验证流程（短信/邮件验证码），不走本端点。
     *
     * <p>与 {@code UserAdminController.update} 的区别：Admin 端可以修改
     * 任意用户的昵称、部门、手机号、邮箱；App 端只能修改自己的昵称和头像。
     */
    @PutMapping("/profile")
    public void updateProfile(@AuthenticationPrincipal Long userId,
                              @Valid @RequestBody UserProfileUpdateRequest request) {
        UserDO user = userService.getUser(userId);
        UserAppConverter.applyProfileUpdate(request, user);
        userService.updateUser(user);
    }
}
