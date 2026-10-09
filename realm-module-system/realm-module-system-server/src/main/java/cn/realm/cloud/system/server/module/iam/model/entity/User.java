package cn.realm.cloud.system.server.module.iam.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * IAM-用户表
 * </p>
 *
 * @author QI Guang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("iam_user")
@Schema(name = "User", description = "IAM-用户表")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（用户ID）
     */
    @Schema(description = "主键ID（用户ID）")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户账号：租户内唯一
     */
    @TableField("username")
    @Schema(description = "用户账号：租户内唯一")
    private String username;

    /**
     * 密码哈希
     */
    @TableField("password_hash")
    @Schema(description = "密码哈希")
    private String passwordHash;

    /**
     * 用户昵称
     */
    @TableField("nickname")
    @Schema(description = "用户昵称")
    private String nickname;

    /**
     * 真实姓名
     */
    @TableField("real_name")
    @Schema(description = "真实姓名")
    private String realName;

    /**
     * 描述
     */
    @TableField("description")
    @Schema(description = "描述")
    private String description;

    /**
     * 部门ID：关联 iam_department.id
     */
    @TableField("department_id")
    @Schema(description = "部门ID：关联 iam_department.id")
    private Long departmentId;

    /**
     * 用户邮箱
     */
    @TableField("email")
    @Schema(description = "用户邮箱")
    private String email;

    /**
     * 手机号码
     */
    @TableField("phone_number")
    @Schema(description = "手机号码")
    private String phoneNumber;

    /**
     * 性别：0-未知；1-男；2-女
     */
    @TableField("gender")
    @Schema(description = "性别：0-未知；1-男；2-女")
    private Integer gender;

    /**
     * 头像地址
     */
    @TableField("avatar_url")
    @Schema(description = "头像地址")
    private String avatarUrl;

    /**
     * 状态：0-正常；1-停用
     */
    @TableField("status")
    @Schema(description = "状态：0-正常；1-停用")
    private Integer status;

    /**
     * 最后登录 IP
     */
    @TableField("last_login_ip")
    @Schema(description = "最后登录 IP")
    private String lastLoginIp;

    /**
     * 最后登录时间
     */
    @TableField("last_login_time")
    @Schema(description = "最后登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    /**
     * 租户ID，0 表示全局默认；关联 saas_tenant.id
     */
    @TableField("tenant_id")
    @Schema(description = "租户ID，0 表示全局默认；关联 saas_tenant.id")
    private Long tenantId;

    /**
     * 逻辑删除：0-未删除；1-已删除
     */
    @TableLogic
    @TableField("is_deleted")
    @Schema(description = "逻辑删除：0-未删除；1-已删除")
    private Integer isDeleted;

    /**
     * 乐观锁版本号
     */
    @TableField("version")
    @Schema(description = "乐观锁版本号")
    private Integer version;

    /**
     * 创建人ID
     */
    @TableField("create_by")
    @Schema(description = "创建人ID")
    private Long createBy;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新人ID
     */
    @TableField("update_by")
    @Schema(description = "更新人ID")
    private Long updateBy;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 有效用户名；逻辑删除下唯一约束辅助列，is_deleted=0 时为 username，否则为 NULL
     */
    @TableField("active_username")
    @Schema(description = "有效用户名；逻辑删除下唯一约束辅助列，is_deleted=0 时为 username，否则为 NULL")
    private String activeUsername;

    /**
     * 有效邮箱；逻辑删除下唯一约束辅助列，is_deleted=0 时为 email，否则为 NULL
     */
    @TableField("active_email")
    @Schema(description = "有效邮箱；逻辑删除下唯一约束辅助列，is_deleted=0 时为 email，否则为 NULL")
    private String activeEmail;

    /**
     * 有效手机号；逻辑删除下唯一约束辅助列，is_deleted=0 时为 phone_number，否则为 NULL
     */
    @TableField("active_phone_number")
    @Schema(description = "有效手机号；逻辑删除下唯一约束辅助列，is_deleted=0 时为 phone_number，否则为 NULL")
    private String activePhoneNumber;
}
