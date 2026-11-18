package cn.realm.cloud.workforce.identity.internal.captcha.adapter.inbound.web.admin;

import cn.realm.cloud.framework.web.security.ClientIpResolver;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Captcha domain — Admin view.
 * 验证码域【管理后台视图】验证码资源入口。
 *
 * <p>面向管理后台的图形验证码接口，用于登录、注册、找回密码等匿名流程，
 * 接口使用 {@link PermitAll} 放行，无需登录鉴权。
 * 与其他视图（如应用端视图、开放平台视图）在权限模型、DTO契约、可见接口集合上严格隔离。
 *
 * <p><b>分层职责边界（Clean Architecture）：</b>
 * <ul>
 *   <li>本 Controller：仅 HTTP 协议适配、OpenAPI 契约声明、端点路由声明；</li>
 *   <li>验证码生成、缓存、校验逻辑下沉至 {@link CaptchaService}；</li>
 *   <li>不包含验证码生成算法、缓存读写等业务逻辑。</li>
 * </ul>
 *
 * <p>验证码流程采用「获取 → 校验 → 二次校验」三段式：
 * 前端调用 {@code /get} 获取验证码图片，用户输入后调用 {@code /check} 完成初步比对；
 * 初步校验通过返回凭证，后续受保护业务接口自行调用校验方法完成二次校验。
 * 当前控制器仅暴露前两步端点。
 *
 * <p>业务编排委托：{@link CaptchaService}。
 *
 * @author QI Guang
 * @see <a href="https://google.aip.dev/122">AIP-122 Resource names</a>
 * @see <a href="https://google.aip.dev/132">AIP-132 Standard List methods</a>
 */
@Slf4j
@Tag(name = "Captcha-Admin", description = "验证码域｜B端管理后台接口")
@RestController
@RequestMapping("/admin/captcha")
@RequiredArgsConstructor
public class CaptchaAdminController {

    private final CaptchaService captchaService;

    private final ClientIpResolver clientIpResolver;

    /**
     * 获取图形验证码。
     *
     * <p>把客户端标识（IP + User-Agent）写入 {@link CaptchaVO#setBrowserInfo(String)}，
     * 供验证码服务做频率限制与风险控制。返回体包含 Base64 图片和本次验证码的 token。
     *
     * @param data    验证码请求参数，包含场景类型等
     * @param request HTTP 请求，用于提取客户端标识
     * @return 验证码响应，包含图片 Base64 和 token
     */
    @PostMapping("/get")
    @Operation(summary = "获得验证码")
    @PermitAll
    // @TenantIgnore  // 验证码与租户无关；若多租户拦截器会误拦，取消本行注释
    public ResponseModel get(@Valid @RequestBody CaptchaVO data, HttpServletRequest request) {
        data.setBrowserInfo(clientIpResolver.resolveClientId(request));
        log.debug("获取验证码, clientId: {}", data.getBrowserInfo());
        return captchaService.get(data);
    }

    /**
     * 校验图形验证码。
     *
     * <p>用户输入的文本由前端放入 {@link CaptchaVO#getPointJson()}（AJ-Captcha 未为文本输入
     * 预留专用字段，本实现复用 pointJson）。校验通过后返回二次校验凭证，
     * 由需要保护的业务接口调用 {@code verification} 完成最终确认。
     *
     * @param data    验证码校验参数，包含 token 和用户输入（pointJson）
     * @param request HTTP 请求，用于提取客户端标识
     * @return 校验结果；成功时携带二次校验凭证
     */
    @PostMapping("/check")
    @Operation(summary = "校验验证码")
    @PermitAll
    // @TenantIgnore
    public ResponseModel check(@Valid @RequestBody CaptchaVO data, HttpServletRequest request) {
        data.setBrowserInfo(clientIpResolver.resolveClientId(request));
        log.debug("校验验证码, clientId: {}", data.getBrowserInfo());
        return captchaService.check(data);
    }

}
