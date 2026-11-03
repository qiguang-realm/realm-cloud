package cn.realm.cloud.system.server.role.internal.api.web.admin.dto;

import cn.realm.cloud.framework.common.pojo.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 角色分页查询请求。
 *
 * <p>实现 {@link PageRequest} 接口，分页参数的兜底逻辑和
 * {@code toPageParam()} 的转换由接口的 default 方法统一提供——
 * 本 DTO 只承载角色特有的查询条件 {@code roleName} / {@code status}。
 *
 * <p>与 {@code UserPageRequest} / {@code PositionPageRequest} 同构：
 * 都 {@code implements PageRequest}，都只有 {@code pageNo} / {@code pageSize}
 * 两个分页字段 + 各自的业务查询字段。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数，最大 100
 * @param roleName 角色名称，模糊匹配
 * @param status   状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "RolePageRequest", description = "角色分页查询请求")
public record RolePageRequest(

        @Schema(description = "页码，从 1 开始", defaultValue = "1")
        @Min(1)
        Integer pageNo,

        @Schema(description = "每页条数，最大 100", defaultValue = "10")
        @Min(1) @Max(100)
        Integer pageSize,

        @Schema(description = "角色名称，模糊匹配")
        String roleName,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status
) implements PageRequest {
}
