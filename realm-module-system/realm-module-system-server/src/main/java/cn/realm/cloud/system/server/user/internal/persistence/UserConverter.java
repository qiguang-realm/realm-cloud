package cn.realm.cloud.system.server.user.internal.persistence;

import cn.realm.cloud.system.server.user.UserDto;

/**
 * {@link UserDO} 与跨模块契约 {@link UserDto} 之间的转换。
 *
 * <p>只处理持久化对象与公共 DTO 的映射，不涉及各端 VO。
 * Admin/App 端的 VO 转换在各自的 {@code internal.api.*.converter} 下完成，
 * 避免 {@code persistence} 反向依赖 {@code api}。
 *
 * <p><b>不映射 passwordHash：</b>{@code UserDto} 是跨模块只读契约，
 * 会被 {@code role} / {@code menu} / {@code organization} 等模块在展示场景
 * 使用，绝不能含密码哈希。登录凭据走 {@code UserCredentialDto}，
 * 由 {@code UserFacadeImpl.toCredential} 单独映射。
 *
 * @author QI Guang
 */
public final class UserConverter {

    private UserConverter() {
    }

    /**
     * {@code UserDO} → {@link UserDto}。
     *
     * @param user 持久化对象；为 {@code null} 时返回 {@code null}
     * @return 跨模块只读契约；不含 {@code passwordHash}
     */
    public static UserDto toDto(UserDO user) {
        if (user == null) {
            return null;
        }
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getDepartmentId(),
                user.getStatus(),
                user.getTenantId()
        );
    }
}
