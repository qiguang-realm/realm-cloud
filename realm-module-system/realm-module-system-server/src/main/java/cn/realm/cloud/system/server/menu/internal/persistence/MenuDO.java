package cn.realm.cloud.system.server.menu.internal.persistence;

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
 * IAM-菜单权限表持久化对象。
 *
 * <p>菜单是 RBAC 中被授权的资源定义，以 {@code parentId} + {@code ancestors} 组织为树形结构。
 * 类型 {@code menuType}：1-目录、2-菜单、3-按钮；按钮通常无 {@code path}/{@code component}，
 * 只携带 {@code permissionCode} 供运行时权限校验。
 *
 * <p>{@code activePermissionCode} 是逻辑删除下的唯一约束辅助列：
 * {@code isDeleted=0} 时等于 {@code permissionCode}，否则为 {@code null}，以释放唯一约束。
 *
 * @author QI Guang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("iam_menu")
@Schema(name = "MenuDO", description = "IAM-菜单权限表")
public class MenuDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID（菜单ID）")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("menu_name")
    @Schema(description = "菜单名称")
    private String menuName;

    @TableField("permission_code")
    @Schema(description = "权限标识：如 system:user:query")
    private String permissionCode;

    @TableField("menu_type")
    @Schema(description = "菜单类型：1-目录；2-菜单；3-按钮")
    private Integer menuType;

    @TableField("sort_order")
    @Schema(description = "显示顺序")
    private Integer sortOrder;

    @TableField("parent_id")
    @Schema(description = "父级ID：0-顶级")
    private Long parentId;

    @TableField("ancestors")
    @Schema(description = "祖级列表，逗号分隔")
    private String ancestors;

    @TableField("path")
    @Schema(description = "路由地址")
    private String path;

    @TableField("icon")
    @Schema(description = "菜单图标")
    private String icon;

    @TableField("component")
    @Schema(description = "前端组件路径")
    private String component;

    @TableField("component_name")
    @Schema(description = "前端组件名")
    private String componentName;

    @TableField("status")
    @Schema(description = "状态：0-正常；1-停用")
    private Integer status;

    @TableField("is_visible")
    @Schema(description = "是否可见：0-否；1-是")
    private Integer isVisible;

    @TableField("is_keep_alive")
    @Schema(description = "是否缓存：0-否；1-是")
    private Integer isKeepAlive;

    @TableField("is_always_show")
    @Schema(description = "是否总是显示：0-否；1-是")
    private Integer isAlwaysShow;

    @TableField("tenant_id")
    @Schema(description = "租户ID，0 表示全局默认；关联 saas_tenant.id")
    private Long tenantId;

    @TableField("is_deleted")
    @Schema(description = "逻辑删除：0-未删除；1-已删除")
    private Integer isDeleted;

    @TableField("version")
    @Schema(description = "乐观锁版本号")
    private Integer version;

    @TableField("create_by")
    @Schema(description = "创建人ID")
    private Long createBy;

    @TableField("create_time")
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField("update_by")
    @Schema(description = "更新人ID")
    private Long updateBy;

    @TableField("update_time")
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableField("active_permission_code")
    @Schema(description = "有效权限标识；逻辑删除下唯一约束辅助列")
    private String activePermissionCode;
}
