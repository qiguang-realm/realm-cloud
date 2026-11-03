package cn.realm.cloud.framework.security.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求。
 */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
}
