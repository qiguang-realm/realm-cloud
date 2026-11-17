package cn.realm.cloud.workforce.identity.api.remote.user.dto;

import java.io.Serializable;

/**
 * 用户模块远程调用响应。
 *
 * <p>使用 {@code record} 保证不可变性，符合 API 契约的最佳实践。
 */
public record UserResponse(
        Long id,
        String username,
        String nickname,
        Long departmentId,
        Integer status
) implements Serializable {
}
