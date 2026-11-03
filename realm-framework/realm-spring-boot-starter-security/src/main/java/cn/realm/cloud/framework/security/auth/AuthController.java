package cn.realm.cloud.framework.security.auth;

import cn.realm.cloud.framework.security.auth.dto.CaptchaResponse;
import cn.realm.cloud.framework.security.auth.dto.LoginRequest;
import cn.realm.cloud.framework.security.auth.dto.LoginResponse;
import cn.realm.cloud.framework.security.auth.dto.RefreshTokenRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证端点。
 *
 * <p>路径通过 {@code realm.security.auth.path} 可配置（默认 /auth）。
 *
 * <p>依据：Google / Microsoft 管理 API 的公开实践——管理端不暴露私有会话 Cookie，
 * 前端通过标准 Authorization 头携带 Bearer Token。本端点仅签发令牌，不建立服务端 Session。
 *
 * @author QI Guang
 */
@RestController
@RequestMapping("${realm.security.auth.path:/auth}")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public void logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/captcha")
    public CaptchaResponse captcha() {
        return authService.generateCaptcha();
    }
}
