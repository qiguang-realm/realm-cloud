package cn.realm.cloud.system.server.organization;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 部门只读契约。
 *
 * <p>跨模块传递的部门信息，<b>不包含</b>审计字段、逻辑删除标记、
 * {@code activeDepartmentName} 等内部字段。字段变更会影响所有依赖
 * {@code organization} 模块的其他模块（{@code user}、{@code role}），
 * 新增字段需评估兼容性，删除/重命名字段是破坏性变更。
 *
 * <p><b>不包含 {@code ancestors}：</b>服务端子树查询用的持久化辅助列，
 * 跨模块场景不需要——其他模块要么只关心“部门是否存在”，要么只关心
 * “部门叫什么”，不会自己去组装部门树（那是 {@code organization} 的职责，
 * 通过 {@code DepartmentAdminController.tree} 或未来的
 * {@code OrganizationFacade.listTree()} 提供）。
 *
 * <p>本类型不可变，由 {@code OrganizationFacadeImpl} 从
 * {@code DepartmentDO} 映射生成。
 *
 * @param id             部门 ID
 * @param departmentName 部门名称
 * @param parentId       父部门 ID；根节点为 {@code null} 或 {@code 0}
 * @param status         状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "DepartmentDto", description = "部门只读契约")
public record DepartmentDto(

        @Schema(description = "部门 ID")
        Long id,

        @Schema(description = "部门名称")
        String departmentName,

        @Schema(description = "父部门 ID；根节点为 null 或 0")
        Long parentId,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status
) {
}
