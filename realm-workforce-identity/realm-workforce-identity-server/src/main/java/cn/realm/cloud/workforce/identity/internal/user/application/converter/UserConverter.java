package cn.realm.cloud.workforce.identity.internal.user.application.converter;

import cn.realm.cloud.workforce.identity.api.facade.dto.UserDto;
import cn.realm.cloud.workforce.identity.internal.user.infrastructure.persistence.UserDO;

/**
 * {@link UserDO} 与跨模块契约 {@link UserDto} 之间的转换。
 *
 * <p><b>架构定位：</b>本类位于应用层。应用层负责将内部的基础设施数据
 * 组装为对外暴露的契约对象（{@code -api} 模块），以避免基础设施层
 * （{@code infrastructure}）反向依赖契约层。
 *
 * <p>只处理持久化对象与公共 DTO 的映射，不涉及各端 VO。
 * Admin/App 端的 VO 转换在各自的 {@code internal.api.*.converter} 下完成。
 *
 * <p><b>不映射 passwordHash：</b>{@code UserDto} 是跨模块只读契约，
 * 会被 {@code role} / {@code menu} / {@code organization} 等模块在展示场景
 * 使用，绝不能含密码哈希。登录凭据走 {@code UserCredentialDto}，
 * 由 {@code UserPrincipalProvider} 单独映射。
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
