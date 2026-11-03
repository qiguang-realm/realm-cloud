package cn.realm.cloud.system.server.organization.internal.api.web.admin.converter;

import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.DepartmentCreateRequest;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.DepartmentResponse;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.DepartmentUpdateRequest;
import cn.realm.cloud.system.server.organization.internal.persistence.DepartmentDO;

import java.util.List;

/**
 * 管理后台 Request/Response 与 {@link DepartmentDO} 的转换。
 *
 * <p>只服务于 {@code internal.api.web.admin} 下的 Controller，
 * 不跨端复用——App 端若需要部门，应使用独立的窄 Response，
 * 避免把管理字段（如负责人用户 ID、联系方式）暴露给 C 端。
 *
 * <p><b>职责边界：</b>本类只做<b>字段映射</b>，不负责树组装、
 * 不负责 {@code ancestors} 计算、不负责排序、不负责
 * {@code managerUserId} 的存在性校验（由 Service 通过
 * {@code UserFacade} 完成）。
 *
 * <p><b>不映射的字段：</b>{@code ancestors}（服务端子树查询辅助列）、
 * {@code activeDepartmentName}（逻辑删除唯一约束辅助列）、
 * {@code tenantId} / {@code createBy} / {@code updateBy} /
 * {@code version} / {@code isDeleted}（租户、审计与并发控制）。
 *
 * @author QI Guang
 */
public final class DepartmentAdminConverter {

    private DepartmentAdminConverter() {
    }

    // ==================== 入站：Request → DO ====================

    /**
     * 创建请求 → 新 {@link DepartmentDO}。
     *
     * <p>只映射请求中允许客户端提供的字段。{@code ancestors} 由服务端
     * 根据 {@code parentId} 计算，{@code createTime}、{@code createBy}
     * 等审计字段由持久化层或服务层补齐，均不在本方法中设置。
     */
    public static DepartmentDO fromCreateRequest(DepartmentCreateRequest request) {
        if (request == null) {
            return null;
        }
        DepartmentDO department = new DepartmentDO();
        department.setParentId(request.parentId());
        department.setDepartmentName(request.departmentName());
        department.setSortOrder(request.sortOrder());
        department.setManagerUserId(request.managerUserId());
        department.setPhoneNumber(request.phoneNumber());
        department.setEmail(request.email());
        department.setStatus(request.status());
        return department;
    }

    /**
     * 更新请求 → 应用到已有 {@link DepartmentDO}。
     *
     * <p>不包含 {@code parentId} 和 {@code status}：前者由独立的 move
     * 端点处理（会触发整棵子树的 {@code ancestors} 重算），后者走独立的
     * 状态变更端点。
     */
    public static void applyUpdateRequest(DepartmentUpdateRequest request,
                                          DepartmentDO department) {
        if (request == null || department == null) {
            return;
        }
        department.setDepartmentName(request.departmentName());
        department.setSortOrder(request.sortOrder());
        department.setManagerUserId(request.managerUserId());
        department.setPhoneNumber(request.phoneNumber());
        department.setEmail(request.email());
    }

    // ==================== 出站：DO → Response ====================

    /**
     * {@link DepartmentDO} → 详情响应（不含子节点）。
     *
     * <p>等价于 {@code toResponse(department, List.of())}，供详情接口使用。
     */
    public static DepartmentResponse toResponse(DepartmentDO department) {
        return toResponse(department, List.of());
    }

    /**
     * {@link DepartmentDO} → 树节点响应，携带已组装好的子节点。
     *
     * <p>供 {@code DepartmentService.listTree} 自底向上组装树时逐层调用：
     * 先构造叶子节点的 {@code DepartmentResponse}（{@code children} 为空列表），
     * 再构造父节点时把子节点列表传入。由于 {@code DepartmentResponse} 是
     * 不可变 {@code record}，不能用“先建父节点再塞子节点”的命令式写法，
     * 必须自底向上构造。
     *
     * @param department 部门持久化对象
     * @param children   已组装好的子节点列表；{@code null} 时由
     *                   {@code DepartmentResponse} 的紧凑构造器归一为空列表
     */
    public static DepartmentResponse toResponse(DepartmentDO department,
                                                List<DepartmentResponse> children) {
        if (department == null) {
            return null;
        }
        return new DepartmentResponse(
                department.getId(),
                department.getParentId(),
                department.getDepartmentName(),
                department.getSortOrder(),
                department.getManagerUserId(),
                department.getPhoneNumber(),
                department.getEmail(),
                department.getStatus(),
                department.getCreateTime(),
                children
        );
    }

    /**
     * 扁平列表映射：{@code List<DepartmentDO>} → {@code List<DepartmentResponse>}（不含子节点）。
     *
     * <p>只用于确实需要“扁平 Response 列表”的场景；树形查询请直接用
     * {@code DepartmentService.listTree()}，它返回的已经是组装好的树。
     */
    public static List<DepartmentResponse> toResponseList(List<DepartmentDO> departments) {
        if (departments == null || departments.isEmpty()) {
            return List.of();
        }
        return departments.stream()
                .map(DepartmentAdminConverter::toResponse)
                .toList();
    }
}
