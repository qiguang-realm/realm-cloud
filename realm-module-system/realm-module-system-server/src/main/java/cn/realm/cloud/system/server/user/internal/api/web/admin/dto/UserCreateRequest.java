package cn.realm.cloud.system.server.user.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 创建用户请求。
 *
 * <p>{@code password} 是明文密码，由 Controller 透传给
 * {@code UserService.createUser(UserDO, String)}，Service 层用
 * {@code PasswordEncoder} 加密后设置到 {@code iam_user.password_hash}。
 * Controller 和 DTO 都不接触加密逻辑——加密是 Service 层的职责。
 *
 * <p><b>密码策略（NIST SP 800-63B-4）：</b>
 * <ul>
 *   <li>只限制最小长度（8 位），<b>不强制</b>大小写 + 数字 + 特殊字符的
 *       组合规则——NIST 明确指出复杂度规则会让用户选择可预测的密码
 *       （如 {@code Password1!}），降低实际安全性</li>
 *   <li>最大长度 64 位，兼容 BCrypt 的 72 字节输入上限（UTF-8 编码下
 *       中文字符占 3 字节，64 个字符最多 192 字节，BCrypt 会截断——
 *       但当前项目以 ASCII 密码为主，64 位足够；如果需要支持长密码，
 *       改用 Argon2 或 PBKDF2）</li>
 *   <li>不在此处校验“是否在已泄露密码列表中”——那需要额外的
 *       HaveIBeenPwned 查询或本地字典，属于 Service 层的增强校验，
 *       不在 DTO 层做</li>
 * </ul>
 *
 * @param username     用户账号，租户内唯一
 * @param password     初始明文密码
 * @param nickname     用户昵称
 * @param departmentId 部门 ID
 * @param phoneNumber  手机号码
 * @param email        用户邮箱
 * @param status       状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "UserCreateRequest", description = "创建用户请求")
public record UserCreateRequest(

        @Schema(description = "用户账号，租户内唯一")
        @NotBlank @Size(max = 64)
        String username,

        @Schema(description = "初始密码，8-64 位")
        @NotBlank @Size(min = 8, max = 64)
        String password,

        @Schema(description = "用户昵称")
        @NotBlank @Size(max = 64)
        String nickname,

        @Schema(description = "部门 ID")
        Long departmentId,

        @Schema(description = "手机号码")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phoneNumber,

        @Schema(description = "用户邮箱")
        @Email
        String email,

        @Schema(description = "状态：0-正常；1-停用")
        @NotNull @Min(0) @Max(1)
        Integer status
) {
}
