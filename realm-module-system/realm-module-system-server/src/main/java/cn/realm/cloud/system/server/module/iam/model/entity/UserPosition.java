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
 * IAM-用户岗位关联表
 * </p>
 *
 * @author QI Guang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("iam_user_position")
@Schema(name = "UserPosition", description = "IAM-用户岗位关联表")
public class UserPosition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
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
     * 岗位ID：关联 iam_position.id
     */
    @TableField("position_id")
    @Schema(description = "岗位ID：关联 iam_position.id")
    private Long positionId;

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
