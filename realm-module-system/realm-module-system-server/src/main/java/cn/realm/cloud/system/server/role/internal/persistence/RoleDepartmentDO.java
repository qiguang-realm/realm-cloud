package cn.realm.cloud.system.server.role.internal.persistence;

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
 * IAM-角色与部门关联表持久化对象。
 *
 * <p>表达角色在 {@code dataScope = 2}（自定义）时可访问的部门范围。
 * 归属 {@code role} 模块——角色是数据范围配置的主导方。
 * 部门本身的合法性由 {@code OrganizationFacade.existsDepartments} 校验，
 * 部门删除后由 {@code DepartmentDeletedListener} 清理本表残留关联。
 *
 * @author QI Guang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("iam_role_department")
@Schema(name = "RoleDepartmentDO", description = "IAM-角色与部门关联表")
public class RoleDepartmentDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 角色ID：关联 iam_role.id
     */
    @TableField("role_id")
    @Schema(description = "角色ID：关联 iam_role.id")
    private Long roleId;

    /**
     * 部门ID：关联 iam_department.id
     */
    @TableField("department_id")
    @Schema(description = "部门ID：关联 iam_department.id")
    private Long departmentId;

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
