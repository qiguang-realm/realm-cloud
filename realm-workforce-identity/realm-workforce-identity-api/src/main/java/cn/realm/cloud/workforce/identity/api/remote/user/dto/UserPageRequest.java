package cn.realm.cloud.workforce.identity.api.remote.user.dto;

import cn.realm.cloud.framework.core.dto.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * 用户模块远程分页查询请求。
 *
 * <p><b>设计依据：</b>继承自 {@link PageParam}，复用框架提供的
 * 页码、每页条数默认值兜底与边界校验（最大 100 条），避免重复定义。
 *
 * <p>字段比管理后台的分页请求可能更窄——远程调用方通常只按用户名或状态筛选，
 * 如需增加筛选字段，先确认有真实调用方，再扩展本类。
 *
 * @author QI Guang
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserPageRequest extends PageParam {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户名模糊匹配；为 {@code null} 或空白时不过滤
     */
    private String username;

    /**
     * 状态精确匹配：0-正常、1-停用；为 {@code null} 时不过滤
     */
    private Integer status;
}
