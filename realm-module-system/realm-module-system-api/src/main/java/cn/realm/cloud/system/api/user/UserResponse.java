package cn.realm.cloud.system.api.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户模块远程调用响应。
 *
 * <p>字段比管理后台的 {@code UserResponse} 更窄——远程调用方通常只需要
 * 判断用户状态、拿到用户名、知道部门归属，不需要手机号、邮箱、创建时间。
 *
 * <p>用可变 class 而非 record 的原因：消费方可能在反序列化后
 * 对字段做补充赋值（如合并多个来源的数据），record 的不可变性
 * 会迫使消费方每次新建实例。代价是类本身不是线程安全的，
 * 但远程 DTO 通常在单次调用内使用，不跨线程共享。
 *
 * @author QI Guang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 部门 ID（可为 {@code null}）
     */
    private Long departmentId;

    /**
     * 状态：0-正常、1-停用
     */
    private Integer status;
}
