package cn.realm.cloud.system.server.user.internal.application;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.user.internal.persistence.UserDO;

import java.util.List;
import java.util.Optional;

/**
 * 用户模块应用服务。
 *
 * <p>编排用户相关的用例：查询、创建、修改状态、删除等。
 * 由入站适配器（{@code internal.api.*} 下的 Controller）和
 * 跨模块门面实现（{@code internal.UserFacadeImpl}）调用。
 * 本接口不暴露给其他模块——其他模块通过根包 {@code UserFacade}
 * 访问用户能力。
 *
 * <p><b>校验职责：</b>本接口是“分页参数合法性”的最终校验点。
 * HTTP 入口由 {@code UserPageRequest} 的 {@code @Min} / {@code @Max}
 * 拦截，内部调用（定时任务、其他 Service）绕过了 Controller，
 * 由本接口的实现类显式校验。Repository / Mapper / 工具类都
 * 不做校验——它们信任 Service 层传入的合法值。
 *
 * <p>迁移初期直接使用 {@link UserDO} 作为输入输出，待业务复杂度
 * 上升后再引入独立的领域对象与 Command/Query 对象。
 *
 * @author QI Guang
 */
public interface UserService {

    // ==================== 查询 ====================

    /**
     * 按主键查询用户，不存在时抛异常。
     *
     * <p>供 Controller 使用——“不存在”应返回 404，而不是静默返回空。
     */
    UserDO getUser(Long id);

    /**
     * 按主键查询用户，不存在时返回空。
     */
    Optional<UserDO> findById(Long id);

    /**
     * 按用户名查询用户。
     */
    Optional<UserDO> findByUsername(String username);

    /**
     * 按邮箱查询用户（未删除）。
     *
     * <p>供 {@code UserFacade.findCredential(EMAIL, email)} 使用——邮箱是
     * 三种登录标识之一，与用户名共用同一个 {@code password_hash}。
     */
    Optional<UserDO> findByEmail(String email);

    /**
     * 按手机号查询用户（未删除）。
     */
    Optional<UserDO> findByPhoneNumber(String phoneNumber);

    /**
     * 判断用户名是否已被占用。
     */
    boolean existsByUsername(String username);

    /**
     * 按主键批量查询用户。
     *
     * <p>入参数量上限由实现类控制（{@code MAX_BATCH_SIZE}）。超过上限时
     * 抛 {@link IllegalArgumentException}，而不是静默截断——截断会让
     * 调用方误以为拿到了完整结果。
     */
    List<UserDO> listByIds(List<Long> ids);

    /**
     * 分页查询用户。
     *
     * <p><b>参数校验：</b>实现类会校验 {@code pageSize} 的范围
     * （{@code 1 ~ PageParam.MAX_PAGE_SIZE}），超出时抛
     * {@link IllegalArgumentException}。调用方不应假设本方法会
     * 接受任意 {@code pageSize}。
     *
     * @param pageParam 分页参数
     * @param username  用户名模糊匹配；为 {@code null} 或空白时不过滤
     * @param status    状态精确匹配；为 {@code null} 时不过滤
     * @return 分页结果
     */
    PageResult<UserDO> page(PageParam pageParam, String username, Integer status);

    // ==================== 命令 ====================

    /**
     * 创建用户。
     *
     * <p><b>密码处理：</b>{@code rawPassword} 是明文密码，实现类会用
     * {@code PasswordEncoder} 加密后设置到 {@code UserDO.passwordHash}。
     * 调用方不应在 {@code user} 中预设 {@code passwordHash}——即使预设了
     * 也会被加密逻辑覆盖。
     *
     * <p><b>唯一性校验：</b>用户名由本方法前置校验（轻量查询）；
     * 邮箱和手机号依赖数据库唯一约束兜底——并发下前置校验不可靠，
     * 唯一约束才是最终防线。
     *
     * @param user        待创建的用户（{@code passwordHash} 由本方法填充）
     * @param rawPassword 明文密码
     * @return 新用户 ID
     * @throws IllegalArgumentException 用户名已存在时抛出
     */
    Long createUser(UserDO user, String rawPassword);

    /**
     * 更新用户。
     *
     * <p>{@code username} 创建后不可修改——它是登录标识，变更会导致
     * 登录日志、审计记录难以追溯。实现中会用数据库中的值覆盖请求值。
     *
     * <p><b>不修改密码：</b>密码修改走独立的 {@code changePassword} 流程，
     * 本方法不会更新 {@code passwordHash}。
     */
    void updateUser(UserDO user);

    /**
     * 变更用户状态（启用 / 停用）。
     *
     * <p>状态变更后发布事件，认证模块监听后使该用户已签发的登录凭证失效。
     */
    void changeStatus(Long id, Integer status);

    /**
     * 删除用户。
     *
     * <p>逻辑删除，并在<b>同一事务内</b>清理同模块的关联表：
     * {@code iam_user_role}、{@code iam_user_position}。这两张表在
     * {@code user} 模块内，直接调用 Repository 清理，不需要事件机制。
     */
    void deleteUser(Long id);
}
