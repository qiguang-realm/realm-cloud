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
 * IAM-菜单权限表
 * </p>
 *
 * @author QI Guang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("iam_menu")
@Schema(name = "Menu", description = "IAM-菜单权限表")
public class Menu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（菜单ID）
     */
    @Schema(description = "主键ID（菜单ID）")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 菜单名称
     */
    @TableField("menu_name")
    @Schema(description = "菜单名称")
    private String menuName;

    /**
     * 权限标识：如 system:user:query
     */
    @TableField("permission_code")
    @Schema(description = "权限标识：如 system:user:query")
    private String permissionCode;

    /**
     * 菜单类型：1-目录；2-菜单；3-按钮
     */
    @TableField("menu_type")
    @Schema(description = "菜单类型：1-目录；2-菜单；3-按钮")
    private Integer menuType;

    /**
     * 显示顺序
     */
    @TableField("sort_order")
    @Schema(description = "显示顺序")
    private Integer sortOrder;

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
     * 路由地址
     */
    @TableField("path")
    @Schema(description = "路由地址")
    private String path;

    /**
     * 菜单图标
     */
    @TableField("icon")
    @Schema(description = "菜单图标")
    private String icon;

    /**
     * 前端组件路径
     */
    @TableField("component")
    @Schema(description = "前端组件路径")
    private String component;

    /**
     * 前端组件名
     */
    @TableField("component_name")
    @Schema(description = "前端组件名")
    private String componentName;

    /**
     * 状态：0-正常；1-停用
     */
    @TableField("status")
    @Schema(description = "状态：0-正常；1-停用")
    private Integer status;

    /**
     * 是否可见：0-否；1-是
     */
    @TableField("is_visible")
    @Schema(description = "是否可见：0-否；1-是")
    private Integer isVisible;

    /**
     * 是否缓存：0-否；1-是
     */
    @TableField("is_keep_alive")
    @Schema(description = "是否缓存：0-否；1-是")
    private Integer isKeepAlive;

    /**
     * 是否总是显示：0-否；1-是
     */
    @TableField("is_always_show")
    @Schema(description = "是否总是显示：0-否；1-是")
    private Integer isAlwaysShow;

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
     * 有效权限标识；逻辑删除下唯一约束辅助列，is_deleted=0 时为 permission_code，否则为 NULL
     */
    @TableField("active_permission_code")
    @Schema(description = "有效权限标识；逻辑删除下唯一约束辅助列，is_deleted=0 时为 permission_code，否则为 NULL")
    private String activePermissionCode;
}
