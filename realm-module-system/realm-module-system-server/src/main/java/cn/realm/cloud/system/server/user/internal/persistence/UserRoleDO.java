package cn.realm.cloud.system.server.user.internal.persistence;

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
 * IAM-用户角色关联表持久化对象。
 *
 * <p>表达"用户拥有这些角色"的关系，归属于 {@code user} 模块——
 * 给用户分配角色是用户编辑的子能力，不是独立资源。
 * 角色本身的合法性由 {@code RoleFacade.existsByIds} 校验，
 * 角色删除后由 {@code RoleDeletedListener} 清理本表残留关联。
 *
 * @author QI Guang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("iam_user_role")
@Schema(name = "UserRoleDO", description = "IAM-用户角色关联表")
public class UserRoleDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID：关联 iam_user.id
     */
    @TableField("user_id")
    @Schema(description = "用户ID：关联 iam_user.id")
    private Long userId;

    /**
     * 角色ID：关联 iam_role.id
     */
    @TableField("role_id")
    @Schema(description = "角色ID：关联 iam_role.id")
    private Long roleId;

    /**
     * 租户ID，0 表示全局默认；关联 saas_tenant.id
     */
    @TableField("tenant_id")
    @Schema(description = "租户ID，0 表示全局默认；关联 saas_tenant.id")
    private Long tenantId;

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
}
