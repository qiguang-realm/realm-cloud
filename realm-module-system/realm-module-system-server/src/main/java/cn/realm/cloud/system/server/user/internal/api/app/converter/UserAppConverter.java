package cn.realm.cloud.system.server.user.internal.api.app.converter;

import cn.realm.cloud.system.server.user.internal.api.app.dto.UserProfileResponse;
import cn.realm.cloud.system.server.user.internal.api.app.dto.UserProfileUpdateRequest;
import cn.realm.cloud.system.server.user.internal.persistence.UserDO;

/**
 * App 端 Request/Response 与 {@link UserDO} 的转换。
 *
 * <p>App 端只返回当前登录用户可见的字段，不包含部门、角色、创建人等管理字段；
 * 手机号<b>始终脱敏</b>后输出（如 {@code 138****5678}），不返回完整号码，
 * 避免与管理后台共享契约造成越权泄漏。
 *
 * <p>与 {@link cn.realm.cloud.system.server.user.internal.api.web.admin.converter.UserAdminConverter}
 * 的区别：Admin 端返回完整手机号（管理需要）、包含部门与状态字段；
 * App 端返回脱敏手机号、只包含个人资料字段。两者不共享 DTO，
 * 各自维护自己的字段可见性。
 *
 * @author QI Guang
 */
public final class UserAppConverter {

    private UserAppConverter() {
    }

    /**
     * {@link UserDO} → 个人资料响应。
     *
     * <p>手机号经 {@link #maskMobile(String)} 脱敏后输出。
     */
    public static UserProfileResponse toProfileResponse(UserDO user) {
        if (user == null) {
            return null;
        }
        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                maskMobile(user.getPhoneNumber())
        );
    }

    /**
     * 个人资料更新请求 → 应用到已有 {@link UserDO}。
     *
     * <p>只允许修改昵称和头像——手机号、邮箱等敏感字段的变更需要
     * 独立的验证流程（短信/邮件验证码），不走本方法。
     *
     * <p>与 {@code UserAdminConverter.applyUpdateRequest} 一样做入口判空：
     * 虽然 Controller 层的 {@code @Valid @RequestBody} 保证非 {@code null}，
     * 但保持同类方法的一致防御风格。
     */
    public static void applyProfileUpdate(UserProfileUpdateRequest request, UserDO user) {
        if (request == null || user == null) {
            return;
        }
        user.setNickname(request.nickname());
        user.setAvatarUrl(request.avatarUrl());
    }

    /**
     * 手机号脱敏。
     *
     * <p>正常 11 位手机号输出 {@code 138****5678}（前 3 + 4 个星号 + 后 4）。
     * 长度不足以安全脱敏（小于 7 位）时返回 {@code "***"}，
     * <b>不返回原值</b>，避免异常短号码被原样暴露。
     *
     * <p>注意：短号码的兜底长度与正常脱敏后的长度不同（3 位 vs 11 位），
     * 前端如果按固定宽度渲染，需要容忍这种差异。返回 {@code "***"}
     * 而不是 {@code null}，是为了区分“手机号异常”与“未填写手机号”
     * 两种情况——后者返回 {@code null}。
     *
     * @param mobile 原始手机号；为 {@code null} 时返回 {@code null}
     * @return 脱敏后的手机号，或 {@code "***"}（短号码），或 {@code null}（未填写）
     */
    private static String maskMobile(String mobile) {
        if (mobile == null) {
            return null;
        }
        if (mobile.length() < 7) {
            return "***";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
