package cn.realm.cloud.system.server.user.internal.persistence;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口。
 *
 * <p>方法签名使用业务语义，不暴露 MyBatis-Plus 的 {@code Wrapper}、{@code IPage}
 * 等类型，使 {@code internal.application} 层与具体 ORM 解耦。
 * 实现由 {@code UserRepositoryImpl} 提供。
 *
 * <p><b>三种登录标识的查询方法：</b>{@link #findByUsername} /
 * {@link #findByEmail} / {@link #findByPhoneNumber} 平行提供，对应
 * {@code iam_user} 上的三个唯一字段。三者共用同一行数据的
 * {@code password_hash}——用户可以用任意一个 + 密码登录。
 *
 * <p><b>逻辑删除约定：</b>{@code UserDO} 未标注 {@code @TableLogic}，
 * 实现必须对<b>所有查询</b>显式追加 {@code is_deleted = 0} 条件，
 * 不能依赖 MyBatis-Plus 的自动过滤。
 *
 * <p><b>职责边界：本接口只做持久化读写，不做参数校验。</b>
 * {@code pageSize} 的取值范围由 {@code UserServiceImpl} 保证——
 * HTTP 入口由 {@code UserPageRequest} 的 {@code @Min} / {@code @Max}
 * 拦截，内部调用由 Service 层显式校验。本接口信任入参已合法。
 *
 * @author QI Guang
 */
public interface UserRepository {

    // ==================== 查询 ====================

    /**
     * 按主键查询用户。
     *
     * @param id 用户 ID；为 {@code null} 时返回 {@link Optional#empty()}
     * @return 未删除的用户；不存在或已逻辑删除时返回空
     */
    Optional<UserDO> findById(Long id);

    /**
     * 按用户名查询用户。
     *
     * <p>用户名是三种登录标识之一。唯一性由逻辑删除辅助列
     * {@code active_username} 保证——未删除用户内用户名唯一。
     *
     * @param username 用户名；为 {@code null} 或空白时返回 {@link Optional#empty()}
     * @return 未删除的用户；不存在时返回空
     */
    Optional<UserDO> findByUsername(String username);

    /**
     * 按邮箱查询用户。
     *
     * <p>邮箱是三种登录标识之一。{@code iam_user.email} 允许为 {@code null}
     * ——为空的用户不能用邮箱登录，查询返回空即可。
     * 唯一性由 {@code active_email} 保证。
     *
     * @param email 邮箱；为 {@code null} 或空白时返回 {@link Optional#empty()}
     * @return 未删除的用户；不存在或该字段为空时返回空
     */
    Optional<UserDO> findByEmail(String email);

    /**
     * 按手机号查询用户。
     *
     * <p>手机号是三种登录标识之一。{@code iam_user.phone_number} 允许为
     * {@code null}——为空的用户不能用手机号登录。唯一性由
     * {@code active_phone_number} 保证。
     *
     * @param phoneNumber 手机号；为 {@code null} 或空白时返回 {@link Optional#empty()}
     * @return 未删除的用户；不存在或该字段为空时返回空
     */
    Optional<UserDO> findByPhoneNumber(String phoneNumber);

    /**
     * 按主键批量查询用户。
     *
     * <p><b>调用方应自己控制入参数量</b>——{@code IN} 子句过长会影响
     * 数据库执行计划。{@code UserService.listByIds} 有 {@code MAX_BATCH_SIZE = 100}
     * 的上限校验，本方法不重复校验（Repository 层不做业务约束）。
     *
     * @param ids 用户 ID 列表；为 {@code null} 或空集合时返回空列表
     * @return 未删除的用户列表，顺序不保证与入参一致
     */
    List<UserDO> findByIds(List<Long> ids);

    // ==================== 存在性判断 ====================

    /**
     * 判断用户名是否已被未删除的用户占用。
     *
     * <p>供 {@code UserServiceImpl.createUser} 在创建前做前置校验。
     * 并发下前置校验不可靠——唯一约束
     * {@code uk_iam_user_tenant_active_username} 才是最终防线。
     *
     * @param username 用户名；为 {@code null} 或空白时返回 {@code false}
     * @return 存在未删除的占用记录时为 {@code true}
     */
    boolean existsByUsername(String username);

    // ==================== 分页 ====================

    /**
     * 分页查询用户，按 {@code id} 升序排列。
     *
     * <p>过滤条件：
     * <ul>
     *   <li>{@code username} 模糊匹配；为 {@code null} 或空白时不过滤</li>
     *   <li>{@code status} 精确匹配；为 {@code null} 时不过滤</li>
     * </ul>
     *
     * <p><b>参数校验：</b>本方法信任 {@code pageParam} 已合法——
     * {@code pageSize} 的取值范围由 {@code UserServiceImpl.page} 保证。
     * 实现直接委托 {@code BaseMapperX.selectPage(PageParam, Wrapper)}，
     * 不做额外的范围检查。
     *
     * @param pageParam 分页参数
     * @param username  用户名模糊匹配；为 {@code null} 或空白时不过滤
     * @param status    状态精确匹配；为 {@code null} 时不过滤
     * @return 分页结果
     */
    PageResult<UserDO> page(PageParam pageParam, String username, Integer status);

    // ==================== 写入 ====================

    /**
     * 保存用户：{@code id} 为 {@code null} 时插入，否则更新。
     *
     * <p><b>三个逻辑删除唯一约束辅助列：</b>{@code activeUsername} /
     * {@code activeEmail} / {@code activePhoneNumber}。实现必须在插入和
     * 更新两个分支都同步维护三者——未删除时等于对应业务字段，字段为
     * 空时辅助列为 {@code null}。漏维护任何一个，逻辑删除后对应的
     * 唯一约束就不会释放，新建同值用户会撞约束。
     *
     * <p><b>更新分支的字段范围：</b>只更新“用户资料”相关字段，
     * <b>不更新</b>以下字段：
     * <ul>
     *   <li>{@code username}——登录标识，创建后不可变</li>
     *   <li>{@code passwordHash}——密码修改走独立流程，不复用本方法</li>
     *   <li>{@code tenantId}——用户不能跨租户迁移</li>
     *   <li>{@code createBy} / {@code createTime}——审计字段，创建后不变</li>
     *   <li>{@code version}——乐观锁版本号，当前未启用乐观锁</li>
     * </ul>
     * 实现应使用 {@code LambdaUpdateWrapper} 显式 {@code set}，不要用
     * {@code updateById}——后者会忽略 {@code null} 字段，导致用户清空
     * 邮箱/手机号/头像时辅助列不被同步清空。
     *
     * @param user 待保存的用户，不能为 {@code null}
     */
    void save(UserDO user);

    /**
     * 逻辑删除用户。
     *
     * <p>实现必须将 {@code isDeleted} 置为 {@code 1}，<b>并显式清空三个辅助列
     * {@code activeUsername} / {@code activeEmail} / {@code activePhoneNumber}</b>
     * 以释放对应的唯一约束。由于 {@code updateById} 忽略 {@code null} 字段，
     * 必须使用 {@code LambdaUpdateWrapper} 显式 {@code set}。
     *
     * <p>同模块关联表 {@code iam_user_role}、{@code iam_user_position}
     * 的清理由 {@code UserServiceImpl.deleteUser} 在事务内调用各自的
     * Repository 完成，本方法只负责 {@code iam_user} 自身。
     *
     * @param id 用户 ID；为 {@code null} 时不做任何操作
     */
    void deleteById(Long id);
}
