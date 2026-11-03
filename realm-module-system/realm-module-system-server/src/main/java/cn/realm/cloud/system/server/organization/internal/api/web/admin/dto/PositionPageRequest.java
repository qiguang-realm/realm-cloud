package cn.realm.cloud.system.server.organization.internal.api.web.admin.dto;

import cn.realm.cloud.framework.common.pojo.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 岗位分页查询请求。
 *
 * <p>实现 {@link PageRequest} 接口，分页参数的兜底逻辑和
 * {@code toPageParam()} 的转换由接口的 default 方法统一提供——
 * 本 DTO 只承载岗位特有的查询条件 {@code positionName} / {@code status}。
 *
 * <p>与 {@code UserPageRequest} / {@code RolePageRequest} 同构：
 * 都 {@code implements PageRequest}，都只有 {@code pageNo} / {@code pageSize}
 * 两个分页字段 + 各自的业务查询字段。
 *
 * <p><b>为什么用 {@code Integer} 而不是 {@code int}：</b>GET 查询参数可能
 * 完全不带 {@code pageNo}，{@code int} 无法表达“未提供”（会默认 {@code 0}，
 * {@code @Min(1)} 直接失败）。用 {@code Integer} 让“未传”这一语义可表达，
 * 兜底由 {@link PageRequest#toPageParam()} 统一处理。
 *
 * <p><b>为什么不在 DTO 里写紧凑构造器兜底：</b>兜底逻辑已经在
 * {@link PageRequest#toPageParam()} 里，再写一次是重复。两次兜底会让
 * “默认值到底在哪一层生效”变得模糊，且三份 DTO 各写一遍容易漏改。
 *
 * @param pageNo       页码，从 1 开始
 * @param pageSize     每页条数，最大 100
 * @param positionName 岗位名称，模糊匹配
 * @param status       状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "PositionPageRequest", description = "岗位分页查询请求")
public record PositionPageRequest(

        @Schema(description = "页码，从 1 开始", defaultValue = "1")
        @Min(1)
        Integer pageNo,

        @Schema(description = "每页条数，最大 100", defaultValue = "10")
        @Min(1) @Max(100)
        Integer pageSize,

        @Schema(description = "岗位名称，模糊匹配")
        String positionName,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status
) implements PageRequest {
}
