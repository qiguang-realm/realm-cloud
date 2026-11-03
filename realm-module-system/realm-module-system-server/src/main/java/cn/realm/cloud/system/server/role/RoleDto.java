package cn.realm.cloud.system.server.role;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色只读契约。
 *
 * <p>跨模块传递的角色信息，<b>不包含</b>审计字段、逻辑删除标记、
 * {@code activeRoleCode} / {@code activeRoleName} 等内部字段。
 * 字段变更会影响所有依赖 {@code role} 模块的其他模块（{@code user}），
 * 新增字段需评估兼容性，删除/重命名字段是破坏性变更。
 *
 * <p><b>不包含 {@code dataScope}：</b>数据范围是角色的权限配置，
 * 由 {@code permission} / 数据权限组件在服务端解析，不通过跨模块
 * DTO 传递。如果某个模块确实需要根据角色的数据范围做过滤，
 * 应当通过专门的权限查询接口，而不是把 {@code dataScope} 放进
 * 通用角色 DTO——那会让 DTO 的职责从“角色标识与展示”膨胀为
 * “权限配置载体”。
 *
 * <p>本类型不可变，由 {@code RoleFacadeImpl} 从 {@code RoleDO} 映射生成。
 *
 * @param id       角色 ID
 * @param roleCode 角色编码
 * @param roleName 角色名称
 * @param status   状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "RoleDto", description = "角色只读契约")
public record RoleDto(

        @Schema(description = "角色 ID")
        Long id,

        @Schema(description = "角色编码")
        String roleCode,

        @Schema(description = "角色名称")
        String roleName,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status
) {
}
