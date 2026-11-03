package cn.realm.cloud.system.server.captcha.internal.api.web.admin;

import cn.hutool.core.util.StrUtil;
import cn.realm.cloud.framework.common.util.servlet.ServletUtils;
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
 * 管理后台 - 验证码接口入口。
 *
 * <p>提供图形验证码的获取与校验能力，服务于登录、注册、找回密码等无需登录即可访问的流程，
 * 因此两个端点均标注 {@link PermitAll}。本类只做 HTTP 协议适配，
 * 验证码的生成、缓存与校验由 {@link CaptchaService} 及其实现承担。
 *
 * <p>验证码采用"获取 → 校验 → 二次校验"三段式：前端先调 {@code /get} 拿到图片，
 * 用户输入后调 {@code /check} 比对，校验通过后由前端拿到的凭证再走业务接口的二次校验。
 * 本类只暴露前两步，二次校验由需要保护的业务接口自行调用 {@code verification}。
 *
 * @author QI Guang
 */
@Slf4j
@Tag(name = "Captcha", description = "管理后台 - 验证码的获取与校验")
@RestController
@RequestMapping("/admin/system/captcha")
@RequiredArgsConstructor
public class CaptchaAdminController {

    private final CaptchaService captchaService;

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
        data.setBrowserInfo(buildClientId(request));
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
        data.setBrowserInfo(buildClientId(request));
        log.debug("校验验证码, clientId: {}", data.getBrowserInfo());
        return captchaService.check(data);
    }

    /**
     * 构建客户端唯一标识（IP + User-Agent）。
     *
     * <p>优先通过 {@link ServletUtils#getClientIP(HttpServletRequest)} 获取真实 IP（支持代理场景），
     * 获取失败时降级为 {@code request.getRemoteAddr()}；User-Agent 为空时替换为 {@code "unknown"}，
     * 避免拼接出空段导致标识歧义。
     *
     * @param request HTTP 请求
     * @return 客户端标识字符串，格式为 {@code ip|ua}，非 null
     */
    private String buildClientId(HttpServletRequest request) {
        String ip = ServletUtils.getClientIP(request);
        if (StrUtil.isBlank(ip)) {
            ip = request.getRemoteAddr();
        }
        String ua = request.getHeader("User-Agent");
        ua = StrUtil.isNotBlank(ua) ? ua : "unknown";
        return ip + "|" + ua;
    }
}
