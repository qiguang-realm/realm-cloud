package cn.realm.cloud.system.server.organization.internal.api.web.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门响应（详情 / 树节点）。
 *
 * <p>{@code children} 为 {@code null} 时归一为空列表，前端可直接遍历。
 *
 * <p><b>不包含 {@code ancestors}：</b>服务端子树查询用的持久化辅助列，
 * 前端渲染树只需要 {@code parentId} + {@code children}。
 *
 * <p><b>不包含 {@code activeDepartmentName}：</b>逻辑删除唯一约束辅助列，
 * 纯持久化细节，不对前端暴露。
 *
 * <p>本类型不可变；树组装由 {@code DepartmentService.listTree}
 * 自底向上构造，每次构造时传入已组装好的 {@code children} 列表。
 *
 * @param id             部门 ID
 * @param parentId       父部门 ID；根节点为 {@code null} 或 {@code 0}
 * @param departmentName 部门名称
 * @param sortOrder      显示顺序
 * @param managerUserId  负责人用户 ID；可为 {@code null}
 * @param phoneNumber    联系电话
 * @param email          联系邮箱
 * @param status         状态：0-正常、1-停用
 * @param createTime     创建时间
 * @param children       子节点列表，非 {@code null}
 * @author QI Guang
 */
@Schema(name = "DepartmentResponse", description = "部门响应")
public record DepartmentResponse(

        @Schema(description = "部门 ID")
        Long id,

        @Schema(description = "父部门 ID；根节点为 null 或 0")
        Long parentId,

        @Schema(description = "部门名称")
        String departmentName,

        @Schema(description = "显示顺序")
        Integer sortOrder,

        @Schema(description = "负责人用户 ID")
        Long managerUserId,

        @Schema(description = "联系电话")
        String phoneNumber,

        @Schema(description = "联系邮箱")
        String email,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status,

        @Schema(description = "创建时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        LocalDateTime createTime,

        @Schema(description = "子节点列表")
        List<DepartmentResponse> children
) {

    /**
     * 紧凑构造器：{@code children} 为 {@code null} 时归一为空列表，
     * 非空时复制为不可变列表，保证 {@code DepartmentResponse} 整体不可变。
     */
    public DepartmentResponse {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
