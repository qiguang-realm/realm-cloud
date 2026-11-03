package cn.realm.cloud.system.server.organization.internal.api.web.admin.converter;

import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.PositionCreateRequest;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.PositionResponse;
import cn.realm.cloud.system.server.organization.internal.api.web.admin.dto.PositionUpdateRequest;
import cn.realm.cloud.system.server.organization.internal.persistence.PositionDO;

import java.util.List;

/**
 * 管理后台 Request/Response 与 {@link PositionDO} 的转换。
 *
 * <p>只服务于 {@code internal.api.web.admin} 下的 Controller。
 * 岗位是平铺列表，没有树形结构，因此本类不提供树节点映射方法，
 * 但提供分页转换 {@link #toPageResponse(PageResult)}。
 *
 * <p><b>职责边界：</b>本类只做<b>字段映射</b>，不负责唯一性校验、
 * 状态变更语义。{@code positionCode} 创建后不可修改，更新请求不包含
 * 该字段，Service 层会用数据库中的原值覆盖。
 *
 * <p><b>不映射的字段：</b>{@code activePositionCode} /
 * {@code activePositionName}（逻辑删除唯一约束辅助列）、
 * {@code tenantId} / {@code createBy} / {@code updateBy} /
 * {@code version} / {@code isDeleted}（租户、审计与并发控制）。
 *
 * @author QI Guang
 */
public final class PositionAdminConverter {

    private PositionAdminConverter() {
    }

    // ==================== 入站：Request → DO ====================

    /**
     * 创建请求 → 新 {@link PositionDO}。
     *
     * <p>只映射请求中允许客户端提供的字段。{@code createTime}、
     * {@code createBy} 等审计字段由持久化层或服务层补齐，
     * 两个逻辑删除辅助列由 {@code PositionRepositoryImpl.save} 维护，
     * 均不在本方法中设置。
     */
    public static PositionDO fromCreateRequest(PositionCreateRequest request) {
        if (request == null) {
            return null;
        }
        PositionDO position = new PositionDO();
        position.setPositionCode(request.positionCode());
        position.setPositionName(request.positionName());
        position.setSortOrder(request.sortOrder());
        position.setStatus(request.status());
        position.setDescription(request.description());
        return position;
    }

    /**
     * 更新请求 → 应用到已有 {@link PositionDO}。
     *
     * <p>不包含 {@code positionCode} 和 {@code status}：前者创建后不可修改，
     * 后者走独立的状态变更端点。
     */
    public static void applyUpdateRequest(PositionUpdateRequest request,
                                          PositionDO position) {
        if (request == null || position == null) {
            return;
        }
        position.setPositionName(request.positionName());
        position.setSortOrder(request.sortOrder());
        position.setDescription(request.description());
    }

    // ==================== 出站：DO → Response ====================

    /**
     * {@link PositionDO} → 详情 / 列表响应。
     */
    public static PositionResponse toResponse(PositionDO position) {
        if (position == null) {
            return null;
        }
        return new PositionResponse(
                position.getId(),
                position.getPositionCode(),
                position.getPositionName(),
                position.getSortOrder(),
                position.getStatus(),
                position.getDescription(),
                position.getCreateTime()
        );
    }

    /**
     * 分页结果转换：{@code PageResult<PositionDO>} → {@code PageResult<PositionResponse>}。
     *
     * <p>保留原分页元数据（总数、页大小、页码），只替换列表元素类型。
     */
    public static PageResult<PositionResponse> toPageResponse(
            PageResult<PositionDO> page) {
        if (page == null) {
            return PageResult.empty();
        }
        List<PositionResponse> list = page.getList().stream()
                .map(PositionAdminConverter::toResponse)
                .toList();
        return PageResult.of(list, page.getTotal(),
                page.getPageSize(), page.getPageNo());
    }
}
