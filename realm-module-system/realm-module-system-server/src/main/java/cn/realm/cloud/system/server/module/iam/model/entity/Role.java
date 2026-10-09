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
 * IAM-角色表
 * </p>
 *
 * @author QI Guang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("iam_role")
@Schema(name = "Role", description = "IAM-角色表")
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（角色ID）
     */
    @Schema(description = "主键ID（角色ID）")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 角色名称
     */
    @TableField("role_name")
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 角色编码：租户内唯一
     */
    @TableField("role_code")
    @Schema(description = "角色编码：租户内唯一")
    private String roleCode;

    /**
     * 显示顺序
     */
    @TableField("sort_order")
    @Schema(description = "显示顺序")
    private Integer sortOrder;

    /**
     * 数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人
     */
    @TableField("data_scope")
    @Schema(description = "数据范围：1-全部；2-自定义；3-本部门；4-本部门及以下；5-仅本人")
    private Integer dataScope;

    /**
     * 状态：0-正常；1-停用
     */
    @TableField("status")
    @Schema(description = "状态：0-正常；1-停用")
    private Integer status;

    /**
     * 描述
     */
    @TableField("description")
    @Schema(description = "描述")
    private String description;

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
     * 有效角色编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 role_code，否则为 NULL
     */
    @TableField("active_role_code")
    @Schema(description = "有效角色编码；逻辑删除下唯一约束辅助列，is_deleted=0 时为 role_code，否则为 NULL")
    private String activeRoleCode;

    /**
     * 有效角色名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 role_name，否则为 NULL
     */
    @TableField("active_role_name")
    @Schema(description = "有效角色名称；逻辑删除下唯一约束辅助列，is_deleted=0 时为 role_name，否则为 NULL")
    private String activeRoleName;
}
