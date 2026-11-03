package cn.realm.cloud.system.server.role.internal.api.web.admin.converter;

import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.role.internal.api.web.admin.dto.RoleCreateRequest;
import cn.realm.cloud.system.server.role.internal.api.web.admin.dto.RoleResponse;
import cn.realm.cloud.system.server.role.internal.api.web.admin.dto.RoleUpdateRequest;
import cn.realm.cloud.system.server.role.internal.persistence.RoleDO;

import java.util.List;

/**
 * 管理后台 Request/Response 与 {@link RoleDO} 的转换。
 *
 * <p>只服务于 {@code internal.api.web.admin} 下的 Controller，
 * 不跨端复用——App 端若需要角色，应使用独立的窄 Response。
 *
 * <p><b>职责边界：</b>本类只做<b>字段映射</b>，不负责唯一性校验、
 * 状态变更语义、{@code dataScope} 变更时清理 {@code iam_role_department}。
 * {@code roleCode} 创建后不可修改，更新请求不包含该字段，
 * Service 层会用数据库中的原值覆盖。
 *
 * <p><b>不映射的字段：</b>{@code activeRoleCode} / {@code activeRoleName}
 * （逻辑删除唯一约束辅助列）、{@code tenantId} / {@code createBy} /
 * {@code updateBy} / {@code version} / {@code isDeleted}（租户、审计与并发控制）。
 *
 * @author QI Guang
 */
public final class RoleAdminConverter {

    private RoleAdminConverter() {
    }

    // ==================== 入站：Request → DO ====================

    /**
     * 创建请求 → 新 {@link RoleDO}。
     *
     * <p>只映射请求中允许客户端提供的字段。{@code createTime}、
     * {@code createBy} 等审计字段由持久化层或服务层补齐，
     * 两个逻辑删除辅助列由 {@code RoleRepositoryImpl.save} 维护，
     * 均不在本方法中设置。
     */
    public static RoleDO fromCreateRequest(RoleCreateRequest request) {
        if (request == null) {
            return null;
        }
        RoleDO role = new RoleDO();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setSortOrder(request.sortOrder());
        role.setDataScope(request.dataScope());
        role.setStatus(request.status());
        role.setDescription(request.description());
        return role;
    }

    /**
     * 更新请求 → 应用到已有 {@link RoleDO}。
     *
     * <p>不包含 {@code roleCode} 和 {@code status}：前者创建后不可修改，
     * 后者走独立的状态变更端点。
     *
     * <p><b>{@code dataScope} 变更的后续处理：</b>如果从 {@code 2}（自定义）
     * 改为其他值，{@code RoleServiceImpl.updateRole} 需要清理
     * {@code iam_role_department} 中的残留关联。本方法只做字段映射，
     * 不触发清理——清理是业务规则，属于 Service 层。
     */
    public static void applyUpdateRequest(RoleUpdateRequest request, RoleDO role) {
        if (request == null || role == null) {
            return;
        }
        role.setRoleName(request.roleName());
        role.setSortOrder(request.sortOrder());
        role.setDataScope(request.dataScope());
        role.setDescription(request.description());
    }

    // ==================== 出站：DO → Response ====================

    /**
     * {@link RoleDO} → 详情 / 分页项响应。
     */
    public static RoleResponse toResponse(RoleDO role) {
        if (role == null) {
            return null;
        }
        return new RoleResponse(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getSortOrder(),
                role.getDataScope(),
                role.getStatus(),
                role.getDescription(),
                role.getCreateTime()
        );
    }

    /**
     * 分页结果转换：{@code PageResult<RoleDO>} → {@code PageResult<RoleResponse>}。
     *
     * <p>保留原分页元数据（总数、页大小、页码），只替换列表元素类型。
     */
    public static PageResult<RoleResponse> toPageResponse(PageResult<RoleDO> page) {
        if (page == null) {
            return PageResult.empty();
        }
        List<RoleResponse> list = page.getList().stream()
                .map(RoleAdminConverter::toResponse)
                .toList();
        return PageResult.of(list, page.getTotal(),
                page.getPageSize(), page.getPageNo());
    }
}
