package cn.realm.cloud.system.server.role.internal.api.web.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 全量替换角色自定义数据范围部门请求。
 *
 * <p>语义是“设置角色的自定义数据范围为这些部门”，而不是逐条增删：
 * 服务端先删除 {@code iam_role_department} 中该角色的所有关联，再插入新列表。
 * 与“逐条 add/remove”的增量接口相比，全量替换让前端只需提交最终状态，
 * 也避免了并发下“两个请求各自增删一半”的中间态。
 *
 * <p><b>只在 {@code dataScope = 2}（自定义）时有意义。</b>
 * 其他数据范围（1-全部、3-本部门、4-本部门及以下、5-仅本人）下，
 * 部门范围由 {@code dataScope} 本身推导，{@code iam_role_department}
 * 中的数据无意义。服务层在写入前会校验角色的 {@code dataScope}，
 * 非自定义范围下拒绝本请求——<b>这个校验不在 DTO 层做</b>，因为
 * DTO 拿不到角色的当前 {@code dataScope}，需要查库才能判断。
 *
 * <p><b>空列表是合法的</b>——表示清空自定义范围。此时角色在数据权限
 * 过滤下将查不到任何部门的数据，前端应在角色编辑页提示用户补充范围。
 * 用 {@code @NotNull} 而不是 {@code @NotEmpty}，就是为了允许“清空”这一语义。
 *
 * <p>元素上的 {@code @NotNull} 防止列表中混入 {@code null}，
 * 避免下游写入 {@code iam_role_department.department_id = null} 这样的脏数据。
 *
 * <p>与 {@link RoleMenuAssignRequest} 同构：统一用 record 包装 {@code List}，
 * 不再接收裸 {@code List<Long>}。
 *
 * @param departmentIds 部门 ID 列表；空列表表示清空自定义范围
 * @author QI Guang
 */
@Schema(name = "RoleDepartmentAssignRequest",
        description = "全量替换角色自定义数据范围部门请求")
public record RoleDepartmentAssignRequest(

        @Schema(description = "部门 ID 列表；空列表表示清空自定义范围")
        @NotNull
        List<@NotNull Long> departmentIds
) {
}
