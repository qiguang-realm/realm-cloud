package cn.realm.cloud.system.server.infra.captcha.controller.admin;

import cn.hutool.core.util.StrUtil;
import cn.realm.cloud.framework.common.util.servlet.ServletUtils;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 验证码 Controller
 * <p>
 * 提供图形验证码的获取和校验接口，使用 Anji Captcha 组件。
 * 所有接口均为公开访问（无需登录）。
 * </p>
 *
 * @author QI Guang
 * @since 1.0
 */
@Tag(name = "管理后台 - 验证码")
@RestController("adminCaptchaController")
@RequestMapping("/system/captcha")
public class CaptchaController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaptchaController.class);

    @Resource
    private CaptchaService captchaService;

    /**
     * 获取图形验证码
     *
     * @param data    验证码请求参数（包含场景类型等）
     * @param request HttpServletRequest，用于获取客户端标识
     * @return 验证码响应（包含图片 base64 和验证码 ID）
     */
    @PostMapping("/get")
    @Operation(summary = "获得验证码")
    @PermitAll
    // @TenantIgnore  // 多租户场景下忽略租户隔离，可根据需要开启
    public ResponseModel get(@Valid @RequestBody CaptchaVO data, HttpServletRequest request) {
        // 设置客户端标识（IP + UA），用于风险控制
        data.setBrowserInfo(buildClientId(request));
        LOGGER.debug("获取验证码，clientId: {}", data.getBrowserInfo());
        return captchaService.get(data);
    }

    /**
     * 校验图形验证码
     *
     * @param data    验证码校验参数（包含验证码 ID 和输入值）
     * @param request HttpServletRequest
     * @return 校验结果
     */
    @PostMapping("/check")
    @Operation(summary = "校验验证码")
    @PermitAll
    // @TenantIgnore
    public ResponseModel check(@Valid @RequestBody CaptchaVO data, HttpServletRequest request) {
        data.setBrowserInfo(buildClientId(request));
        LOGGER.debug("校验验证码，clientId: {}", data.getBrowserInfo());
        return captchaService.check(data);
    }

    /**
     * 构建客户端唯一标识（IP + User-Agent）
     * <p>
     * 优先通过 {@link ServletUtils#getClientIP(HttpServletRequest)} 获取真实 IP，
     * 若获取失败则降级为 {@code request.getRemoteAddr()}。
     * User-Agent 若为空则替换为 "unknown"。
     * </p>
     *
     * @param request HTTP 请求
     * @return 客户端标识字符串（非 null）
     */
    private String buildClientId(HttpServletRequest request) {
        // 获取真实 IP（支持代理）
        String ip = ServletUtils.getClientIP(request);
        if (StrUtil.isBlank(ip)) {
            ip = request.getRemoteAddr();  // 降级
        }
        // 获取 User-Agent，避免 null
        String ua = request.getHeader("User-Agent");
        ua = StrUtil.isNotBlank(ua) ? ua : "unknown";

        // 拼接 IP 和 UA，防止空字符串造成歧义
        return ip + "|" + ua;  // 使用分隔符便于解析
    }
}
