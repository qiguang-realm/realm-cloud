package cn.realm.cloud.system.server.user.internal.api.app;

import cn.realm.cloud.system.server.user.internal.api.app.converter.UserAppConverter;
import cn.realm.cloud.system.server.user.internal.api.app.dto.UserProfileResponse;
import cn.realm.cloud.system.server.user.internal.api.app.dto.UserProfileUpdateRequest;
import cn.realm.cloud.system.server.user.internal.application.UserService;
import cn.realm.cloud.system.server.user.internal.persistence.UserDO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 用户模块 App 端接口入口。
 *
 * <p>面向移动 App、小程序等 C 端消费者的用户接口，由当前登录用户操作
 * 自身数据（查询/修改个人资料），只允许操作
 * {@code authentication.principal} 所对应的用户，<b>不存在跨用户的管理能力</b>
 * ——这是与 {@link cn.realm.cloud.system.server.user.internal.api.web.admin.UserAdminController}
 * 的核心区别：Admin 端通过 {@code @PathVariable} 指定任意用户 ID，
 * App 端只能操作自己。
 *
 * <p><b>为什么不接收 {@code userId} 参数：</b>如果 App 端也接收
 * {@code userId}，即使是“只允许查自己”的逻辑，也给了前端传错 ID
 * 或恶意传他人 ID 的机会，后端必须额外校验“传入的 ID == 当前登录用户 ID”。
 * 直接从安全上下文取当前用户 ID，从结构上消除了越权可能——
 * 不需要校验，因为根本没有可传的参数。
 *
 * <p><b>与管理后台的隔离：</b>本类与管理后台接口在
 * <ul>
 *   <li><b>权限模型</b>上隔离：App 端不校验 {@code system:user:xxx} 权限点，
 *       只依赖“已登录”这一前提</li>
 *   <li><b>请求/响应模型</b>上隔离：App 端使用 {@code UserProfileResponse}，
 *       只包含个人资料字段（昵称、头像、脱敏手机号），不包含部门、角色、
 *       状态、邮箱等管理字段</li>
 * </ul>
 *
 * <p>本类只做 HTTP 协议适配，业务委托给 {@link UserService}。
 *
 * @author QI Guang
 * @see cn.realm.cloud.system.server.user.internal.api.web.admin.UserAdminController
 */
@RestController
@RequestMapping("/app/iam/user")
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
