package cn.realm.cloud.system.api.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户模块远程分页查询请求。
 *
 * <p>字段比管理后台的 {@code UserPageRequest} 可能更窄——远程调用方
 * 通常只按用户名或状态筛选，不需要部门、角色等复合条件。
 * 如需增加筛选字段，先确认有真实调用方，再扩展本类。
 *
 * <p><b>默认值：</b>{@code pageNo} 为 {@code 1}、{@code pageSize} 为 {@code 10}。
 * 消费方不传时使用默认值；显式传 {@code null} 会被 Jackson 覆盖为默认值
 * （字段初始化在反序列化时生效），不会变成 {@code null}。
 *
 * @author QI Guang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 页码，从 1 开始
     */
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1;

    /**
     * 每页条数，上限 100
     */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer pageSize = 10;

    /**
     * 用户名模糊匹配；为 {@code null} 或空白时不过滤
     */
    private String username;

    /**
     * 状态精确匹配：0-正常、1-停用；为 {@code null} 时不过滤
     */
    private Integer status;
}
