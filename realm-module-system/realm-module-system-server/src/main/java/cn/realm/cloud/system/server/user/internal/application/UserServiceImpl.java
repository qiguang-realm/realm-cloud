package cn.realm.cloud.system.server.user.internal.application;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.user.events.UserCreatedEvent;
import cn.realm.cloud.system.server.user.events.UserDeletedEvent;
import cn.realm.cloud.system.server.user.events.UserStatusChangedEvent;
import cn.realm.cloud.system.server.user.internal.persistence.UserDO;
import cn.realm.cloud.system.server.user.internal.persistence.UserPositionRepository;
import cn.realm.cloud.system.server.user.internal.persistence.UserRepository;
import cn.realm.cloud.system.server.user.internal.persistence.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

/**
 * {@link UserService} 的默认实现。
 *
 * <p>数据访问委托给 {@link UserRepository}，不直接依赖 MyBatis-Plus。
 * 删除用户时在同一事务内清理同模块的 {@code iam_user_role}、
 * {@code iam_user_position}——这两张表归 {@code user} 模块所有，
 * 直接调用 Repository 清理，不需要事件机制。
 *
 * <p><b>校验职责：</b>本类是“分页参数合法性”的最终校验点。
 * HTTP 入口由 {@code UserPageRequest} 拦截，内部调用由本类
 * {@link #page(PageParam, String, Integer)} 显式校验。
 * Repository / Mapper / 工具类都不做校验——这是“工具类只做转换、
 * 校验分层”原则的落地。
 *
 * <p>跨模块的关联（如其他模块持有的用户引用）通过发布
 * {@link UserDeletedEvent} 通知，由对应模块异步清理。
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    /**
     * 批量查询的最大数量。
     *
     * <p>防止调用方一次传入过多 ID 导致 {@code IN} 子句过长。
     * 超过上限时直接拒绝，而不是静默截断——截断会让调用方误以为
     * 拿到了完整结果。
     */
    private static final int MAX_BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPositionRepository userPositionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 查询 ====================

    @Override
    public UserDO getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
    }

    @Override
    public Optional<UserDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id);
    }

    @Override
    public Optional<UserDO> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username);
    }

    @Override
    public Optional<UserDO> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<UserDO> findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    @Override
    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userRepository.existsByUsername(username);
    }

    @Override
    public List<UserDO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "批量查询数量不能超过 " + MAX_BATCH_SIZE + "，当前: " + ids.size());
        }
        return userRepository.findByIds(ids);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>参数校验：</b>{@code pageSize} 必须在
     * {@code 1 ~ PageParam.MAX_PAGE_SIZE} 之间。HTTP 入口已由
     * {@code UserPageRequest} 的 {@code @Min} / {@code @Max} 拦截，
     * 本方法再校验一次，覆盖内部调用绕过 Controller 的场景。
     *
     * <p><b>为什么不在 Repository 校验：</b>Repository / Mapper 只做
     * 转换和委托，校验上移到 Service 层——这样无论底层是 MyBatis
     * 还是 JPA，非法参数都在同一层以同一种异常形态暴露，不依赖
     * 底层框架的具体行为。
     */
    @Override
    public PageResult<UserDO> page(PageParam pageParam, String username, Integer status) {
        if (pageParam == null) {
            throw new IllegalArgumentException("PageParam 不能为空");
        }
        int pageSize = pageParam.getPageSizeOrDefault();
        if (pageSize <= 0 || pageSize > PageParam.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize 必须在 1 到 " + PageParam.MAX_PAGE_SIZE
                            + " 之间，当前: " + pageSize);
        }
        return userRepository.page(pageParam, username, status);
    }

    // ==================== 命令 ====================

    /**
     * {@inheritDoc}
     *
     * <p><b>密码处理：</b>明文密码由本方法加密后设置到
     * {@code UserDO.passwordHash}。调用方不应在 {@code user} 中
     * 预设 {@code passwordHash}——即使预设了也会被覆盖。
     *
     * <p><b>唯一性校验：</b>先校验用户名唯一性（最轻的本地查询），
     * 再落库。邮箱和手机号的唯一性依赖数据库唯一约束兜底——
     * 并发下前置校验不可靠，唯一约束才是最终防线。
     */
    @Override
    @Transactional
    public Long createUser(UserDO user, String rawPassword) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("用户名已存在: " + user.getUsername());
        }
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        eventPublisher.publishEvent(
                new UserCreatedEvent(user.getId(), user.getUsername(), user.getDepartmentId()));
        log.info("创建用户成功, id={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>关于 {@code username} 不可修改：</b>即使用户通过 API 传入了
     * 新的 {@code username}，也会被数据库中的原值覆盖。更新请求 DTO
     * 中本来就不包含 {@code username}，但即使有人构造了一个
     * {@code UserDO} 直接调用本方法，也不会改掉登录标识。
     *
     * <p><b>关于 {@code email} / {@code phoneNumber} 的唯一性：</b>
     * 如果被修改，唯一性由数据库约束兜底。本方法不做前置校验——
     * 更新场景的“排除自身”校验（{@code existsByEmailAndIdNot}）
     * 目前没有实现，加了反而需要额外维护；依赖数据库约束更简单。
     * 如果冲突，抛 {@code DuplicateKeyException}，由全局异常处理器
     * 转为友好的错误信息。
     *
     * <p><b>不修改密码：</b>密码修改走独立的 {@code changePassword}
     * 流程。即使调用方在 {@code user} 中传入了不同的
     * {@code passwordHash}，{@code UserRepositoryImpl.save} 的更新分支
     * 也不会 set 该字段——这是双重保护。
     */
    @Override
    @Transactional
    public void updateUser(UserDO user) {
        UserDO existing = getUser(user.getId());
        // 用户名创建后不可修改——它是登录标识，变更会导致登录日志、
        // 审计记录难以追溯。即使请求误传，也用数据库中的值覆盖。
        user.setUsername(existing.getUsername());
        userRepository.save(user);
        log.info("更新用户成功, id={}, username={}", user.getId(), user.getUsername());
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>为什么用事件而非直接调用：</b>用户状态变更后，需要使该用户
     * 已签发的登录凭证失效。这个逻辑在认证模块（starter 的
     * {@code TokenAuthenticationFilter} 或业务侧缓存层），不属于
     * {@code user} 模块的职责。{@code user} 模块发布
     * {@link UserStatusChangedEvent}，订阅方自行处理——这避免了
     * {@code user → auth} 的反向依赖。
     */
    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        UserDO user = getUser(id);
        user.setStatus(status);
        userRepository.save(user);
        eventPublisher.publishEvent(new UserStatusChangedEvent(id, status));
        log.info("变更用户状态, id={}, status={}", id, status);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>清理顺序：</b>先清理同模块关联表，再逻辑删除用户本身，
     * 最后发布事件。在同一事务内，任一步失败都会整体回滚，不会出现
     * “关联清了但用户还在”或“用户删了但关联没清”的中间态。
     *
     * <p><b>为什么关联表直接调用 Repository 而非事件：</b>
     * {@code iam_user_role} / {@code iam_user_position} 归 {@code user}
     * 模块所有，本模块直接清理是聚合内部的变更，不需要跨模块通知。
     */
    @Override
    @Transactional
    public void deleteUser(Long id) {
        getUser(id);
        userRoleRepository.deleteByUserId(id);
        userPositionRepository.deleteByUserId(id);
        userRepository.deleteById(id);
        eventPublisher.publishEvent(new UserDeletedEvent(id));
        log.info("删除用户成功, id={}", id);
    }
}
