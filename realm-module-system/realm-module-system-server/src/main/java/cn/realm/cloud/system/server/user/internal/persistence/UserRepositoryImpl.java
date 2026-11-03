package cn.realm.cloud.system.server.user.internal.persistence;

import cn.realm.cloud.framework.common.pojo.PageParam;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.user.internal.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link UserRepository} 的 MyBatis-Plus 实现。
 *
 * <p>MyBatis-Plus 的 {@code UserMapper} 仅在本类内部使用，不向上泄漏到
 * {@code internal.application}。
 *
 * <p><b>逻辑删除注意：</b>{@code UserDO} 未标注 {@code @TableLogic}，
 * MyBatis-Plus 不会自动追加 {@code is_deleted = 0} 条件。
 * <b>所有查询</b>（包括 {@link #findById}、{@link #findByIds}、
 * {@link #findByUsername} / {@link #findByEmail} / {@link #findByPhoneNumber}）
 * 都必须<b>显式</b>加 {@code eq(UserDO::getIsDeleted, 0)}。遗漏会导致
 * 已删除用户被当作有效数据返回——{@code findById} 之前就踩过这个坑，
 * 三个 {@code findByXxx} 方法不能重蹈。
 *
 * <p><b>{@code null} 字段注意：</b>{@code updateById} 会忽略 {@code null}
 * 字段。用户资料更新时，{@code email} / {@code phone_number} /
 * {@code avatar_url} 等字段可能被清空（比如用户想解绑邮箱），
 * 必须用 {@link LambdaUpdateWrapper} 显式 {@code set}，不能依赖
 * {@code updateById} 的非空字段策略。
 *
 * <p><b>三个辅助列：</b>{@code UserDO} 有三个逻辑删除唯一约束辅助列——
 * {@code activeUsername} / {@code activeEmail} / {@code activePhoneNumber}。
 * {@code save} 的更新分支和 {@code deleteById} 都必须同时维护三个列，
 * 漏一个会导致对应的唯一约束不释放。
 *
 * <p><b>职责边界：本类只做持久化读写，不做参数校验。</b>
 * {@code pageSize} 的取值范围由 {@code UserServiceImpl} 保证。
 *
 * @author QI Guang
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    private final UserMapper userMapper;

    // ==================== 查询 ====================

    @Override
    public Optional<UserDO> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<UserDO>lambdaQuery()
                        .eq(UserDO::getId, id)
                        .eq(UserDO::getIsDeleted, NOT_DELETED)));
    }

    @Override
    public Optional<UserDO> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<UserDO>lambdaQuery()
                        .eq(UserDO::getUsername, username)
                        .eq(UserDO::getIsDeleted, NOT_DELETED)));
    }

    @Override
    public Optional<UserDO> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<UserDO>lambdaQuery()
                        .eq(UserDO::getEmail, email)
                        .eq(UserDO::getIsDeleted, NOT_DELETED)));
    }

    @Override
    public Optional<UserDO> findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<UserDO>lambdaQuery()
                        .eq(UserDO::getPhoneNumber, phoneNumber)
                        .eq(UserDO::getIsDeleted, NOT_DELETED)));
    }

    @Override
    public List<UserDO> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(
                Wrappers.<UserDO>lambdaQuery()
                        .in(UserDO::getId, ids)
                        .eq(UserDO::getIsDeleted, NOT_DELETED));
    }

    @Override
    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userMapper.exists(
                Wrappers.<UserDO>lambdaQuery()
                        .eq(UserDO::getUsername, username)
                        .eq(UserDO::getIsDeleted, NOT_DELETED));
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>直接委托 {@code BaseMapperX.selectPage(PageParam, Wrapper)}
     * ——这是 `BaseMapperX` 提供的 default 方法，内部完成三步：
     * <ol>
     *   <li>{@code MyBatisPageUtils.toPage(pageParam)} 构造 {@code Page}</li>
     *   <li>{@code selectPage(page, wrapper)} 执行查询，分页拦截器生成
     *       {@code LIMIT} 与 {@code COUNT}</li>
     *   <li>{@code MyBatisPageUtils.toPageResult(page)} 转换为框架无关的
     *       {@link PageResult}</li>
     * </ol>
     *
     * <p>与 {@code MenuRepositoryImpl.page} / {@code RoleRepositoryImpl.page}
     * 的结构完全一致——五个 Repository 的分页实现统一走 {@code BaseMapperX}，
     * 未来给 {@code selectPage} 加公共逻辑（租户过滤、分页上限兜底）时，
     * 五个模块自动受益。
     */
    @Override
    public PageResult<UserDO> page(PageParam pageParam, String username, Integer status) {
        LambdaQueryWrapper<UserDO> wrapper = Wrappers.<UserDO>lambdaQuery()
                .eq(UserDO::getIsDeleted, NOT_DELETED)
                .like(username != null && !username.isBlank(), UserDO::getUsername, username)
                .eq(status != null, UserDO::getStatus, status)
                .orderByAsc(UserDO::getId);
        return userMapper.selectPage(pageParam, wrapper);
    }

    // ==================== 写入 ====================

    @Override
    public void save(UserDO user) {
        if (user == null) {
            throw new IllegalArgumentException("user 不能为空");
        }

        String username = user.getUsername();
        boolean hasUsername = username != null && !username.isBlank();
        String email = user.getEmail();
        boolean hasEmail = email != null && !email.isBlank();
        String phoneNumber = user.getPhoneNumber();
        boolean hasPhoneNumber = phoneNumber != null && !phoneNumber.isBlank();

        // ---------- 插入 ----------
        if (user.getId() == null) {
            user.setActiveUsername(hasUsername ? username : null);
            user.setActiveEmail(hasEmail ? email : null);
            user.setActivePhoneNumber(hasPhoneNumber ? phoneNumber : null);
            user.setIsDeleted(NOT_DELETED);
            userMapper.insert(user);
            return;
        }

        // ---------- 更新 ----------
        LambdaUpdateWrapper<UserDO> wrapper = Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getId, user.getId())
                .eq(UserDO::getIsDeleted, NOT_DELETED)
                // 三个唯一约束辅助列
                .set(UserDO::getActiveUsername, hasUsername ? username : null)
                .set(UserDO::getActiveEmail, hasEmail ? email : null)
                .set(UserDO::getActivePhoneNumber, hasPhoneNumber ? phoneNumber : null)
                // 业务字段（不包含 username、passwordHash、tenantId、审计字段）
                .set(UserDO::getNickname, user.getNickname())
                .set(UserDO::getRealName, user.getRealName())
                .set(UserDO::getDescription, user.getDescription())
                .set(UserDO::getDepartmentId, user.getDepartmentId())
                .set(UserDO::getEmail, user.getEmail())
                .set(UserDO::getPhoneNumber, user.getPhoneNumber())
                .set(UserDO::getGender, user.getGender())
                .set(UserDO::getAvatarUrl, user.getAvatarUrl())
                .set(UserDO::getStatus, user.getStatus())
                .set(UserDO::getLastLoginIp, user.getLastLoginIp())
                .set(UserDO::getLastLoginTime, user.getLastLoginTime())
                .set(UserDO::getUpdateBy, user.getUpdateBy())
                .set(UserDO::getUpdateTime, user.getUpdateTime());

        userMapper.update(null, wrapper);
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) {
            return;
        }
        userMapper.update(null,
                Wrappers.<UserDO>lambdaUpdate()
                        .eq(UserDO::getId, id)
                        .eq(UserDO::getIsDeleted, NOT_DELETED)
                        .set(UserDO::getIsDeleted, DELETED)
                        .set(UserDO::getActiveUsername, null)
                        .set(UserDO::getActiveEmail, null)
                        .set(UserDO::getActivePhoneNumber, null));
    }
}
