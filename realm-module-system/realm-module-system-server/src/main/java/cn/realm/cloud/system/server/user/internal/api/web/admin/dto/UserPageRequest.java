package cn.realm.cloud.system.server.user.internal.api.web.admin.dto;

import cn.realm.cloud.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 用户分页查询请求。
 *
 * <p>作为 GET 查询参数绑定，{@code pageNo} / {@code pageSize} 允许不传，
 * 紧凑构造器兜底为 {@code 1} / {@code 10}，保证下游拆箱安全。
 *
 * <p>与 {@code PageParam} 的关系：本类是 HTTP 层契约（承载查询条件 +
 * 分页参数），{@code PageParam} 是框架层分页参数（只承载分页）。
 * {@link #toPageParam()} 负责把分页部分剥离出来传给 Service，
 * 查询条件（{@code username} / {@code status}）作为独立参数传递。
 *
 * <p><b>为什么不直接继承 {@code PageParam}：</b>{@code PageParam} 是
 * 可变类（有 setter），且被所有业务模块共享。让 HTTP 请求继承它会让
 * “HTTP 契约”和“框架分页参数”耦合——修改 {@code PageParam} 会波及
 * 所有继承它的 DTO。用组合（{@link #toPageParam()}）更清晰。
 *
 * <p><b>为什么保留字段兜底而不依赖 {@code PageParam.of()}：</b>
 * {@code @Min} / {@code @Max} 对 {@code null} 不生效，如果完全依赖
 * 校验注解，未传 {@code pageNo} 时 {@code request.pageNo()} 返回
 * {@code null}，下游拆箱会 NPE。紧凑构造器兜底保证 {@code pageNo()} /
 * {@code pageSize()} 永远返回有效值，调用方不需要判空。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数，最大 100
 * @param username 用户名，模糊匹配
 * @param status   状态：0-正常、1-停用
 * @author QI Guang
 */
@Schema(name = "UserPageRequest", description = "用户分页查询请求")
public record UserPageRequest(

        @Schema(description = "页码，从 1 开始", defaultValue = "1")
        @Min(1)
        Integer pageNo,

        @Schema(description = "每页条数，最大 100", defaultValue = "10")
        @Min(1) @Max(100)
        Integer pageSize,

        @Schema(description = "用户名，模糊匹配")
        String username,

        @Schema(description = "状态：0-正常、1-停用")
        Integer status
) {

    /**
     * 紧凑构造器：{@code pageNo} / {@code pageSize} 为 {@code null}
     * 或小于下限时，归一为默认值。这样 Controller 里
     * {@code request.pageNo()} 返回的永远是有效值，拆箱 {@code int}
     * 不会 NPE。
     *
     * <p>注意：{@code @Max(100)} 对 {@code null} 不生效，超出上限的值
     * 由校验器拦截；构造器只兜底下限和 {@code null}，不处理上限——
     * “过大”是应当被明确拒绝的非法输入，不是应当被静默修正的缺省值。
     */
    public UserPageRequest {
        if (pageNo == null || pageNo < 1) {
            pageNo = PageParam.DEFAULT_PAGE_NO;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = PageParam.DEFAULT_PAGE_SIZE;
        }
    }

    /**
     * 把分页部分剥离为框架层的 {@link PageParam}。
     *
     * <p>Service 层接收 {@code PageParam}，不感知 HTTP 层的
     * {@code username} / {@code status}——查询条件作为独立参数传给
     * Service。这样 Service 的分页方法签名在多个调用方（Controller、
     * 定时任务、其他 Service）之间保持一致。
     *
     * <p>由于紧凑构造器已经兜底，这里的 {@code pageNo} / {@code pageSize}
     * 一定非 {@code null} 且 {@code >= 1}，直接构造即可，不需要再判空。
     *
     * @return 分页参数
     */
    public PageParam toPageParam() {
        return PageParam.of(pageNo, pageSize);
    }
}
