package cn.realm.cloud.system.server.module.iam.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
 * IAM-部门表
 * </p>
 *
 * @author QI Guang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("iam_department")
@Schema(name = "Department", description = "IAM-部门表")
public class Department implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（部门ID）
     */
    @Schema(description = "主键ID（部门ID）")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 部门名称
     */
    @Schema(description = "部门名称")
    @TableField("department_name")
    private String departmentName;

    /**
     * 父级ID：0-顶级
     */
    @TableField("parent_id")
    @Schema(description = "父级ID：0-顶级")
    private Long parentId;

    /**
     * 祖级列表，逗号分隔
     */
    @TableField("ancestors")
    @Schema(description = "祖级列表，逗号分隔")
    private String ancestors;

    /**
     * 显示顺序
     */
    @TableField("sort_order")
    @Schema(description = "显示顺序")
    private Integer sortOrder;

    /**
     * 部门负责人用户ID：关联 iam_user.id
     */
    @TableField("manager_user_id")
    @Schema(description = "部门负责人用户ID：关联 iam_user.id")
    private Long managerUserId;

    /**
     * 联系电话
     */
    @TableField("phone_number")
    @Schema(description = "联系电话")
    private String phoneNumber;

    /**
     * 邮箱
     */
    @TableField("email")
    @Schema(description = "邮箱")
    private String email;

    /**
     * 状态：0-正常；1-停用
     */
    @TableField("status")
    @Schema(description = "状态：0-正常；1-停用")
    private Integer status;

    /**
     * 租户ID，0 表示全局默认；关联 saas_tenant.id
     */
    @TableField("tenant_id")
    @Schema(description = "租户ID，0 表示全局默认；关联 saas_tenant.id")
    private Long tenantId;

    /**
     * 逻辑删除：0-未删除；1-已删除
     */
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
     * 有效部门名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 department_name，否则为 NULL
     */
    @TableField("active_department_name")
    @Schema(description = "有效部门名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 department_name，否则为 NULL")
    private String activeDepartmentName;
}
