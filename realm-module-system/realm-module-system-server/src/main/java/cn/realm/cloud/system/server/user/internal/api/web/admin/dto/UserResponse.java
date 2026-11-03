package cn.realm.cloud.system.server.user.internal.api.web.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 用户详情/列表响应。
 *
 * <p>面向管理后台的用户只读视图，包含管理员需要看到的所有字段。
 * 与 App 端的 {@code UserProfileResponse} 隔离——App 端只返回当前用户
 * 可见的字段，管理端返回管理所需的完整信息。
 *
 * <p><b>不包含 {@code passwordHash}：</b>密码哈希绝不能出现在 HTTP 响应中，
 * 即使前端“不使用”它，也会暴露在浏览器的网络面板、日志、链路追踪里。
 * {@code UserAdminConverter.toResponse} 不映射该字段，从物理上保证
 * 密码哈希不离开服务端。
 *
 * <p><b>不包含 {@code tenantId} / {@code isDeleted} / {@code version} /
 * 审计字段：</b>这些是内部管理字段，管理后台不需要感知。租户隔离由
 * 框架层自动处理，逻辑删除在查询时已过滤，版本号和审计字段属于
 * 持久化细节。
 *
 * @param id           用户 ID
 * @param username     用户账号
 * @param nickname     用户昵称
 * @param avatarUrl    头像 URL；可为 {@code null}（用户未上传头像）
 * @param departmentId 部门 ID
 * @param phoneNumber  手机号码
 * @param email        用户邮箱
 * @param status       状态：0-正常、1-停用
 * @param createTime   创建时间
 * @author QI Guang
 */
@Schema(name = "UserResponse", description = "用户详情/列表响应")
public record UserResponse(

        @Schema(description = "用户 ID")
        Long id,

        @Schema(description = "用户账号")
        String username,

        @Schema(description = "用户昵称")
        String nickname,

        @Schema(description = "头像 URL")
        String avatarUrl,

        @Schema(description = "部门 ID")
        Long departmentId,

        @Schema(description = "手机号码")
        String phoneNumber,

        @Schema(description = "用户邮箱")
        String email,

        @Schema(description = "状态：0-正常；1-停用")
        Integer status,

        @Schema(description = "创建时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        LocalDateTime createTime
) {
}
