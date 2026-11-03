package cn.realm.cloud.system.server.user.internal.persistence;

import cn.realm.cloud.system.server.user.internal.persistence.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link UserRoleRepository} 的 MyBatis-Plus 实现。
 *
 * <p>{@link UserRoleMapper} 仅在本类内部使用。关联表没有逻辑删除列，
 * 查询和删除都不需要附加 {@code is_deleted = 0} 条件。
 *
 * @author QI Guang
 */
@Repository
@RequiredArgsConstructor
public class UserRoleRepositoryImpl implements UserRoleRepository {

    private final UserRoleMapper userRoleMapper;

    @Override
    public List<Long> findRoleIdsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userRoleMapper.selectList(
                        Wrappers.<UserRoleDO>lambdaQuery()
                                .eq(UserRoleDO::getUserId, userId))
                .stream()
                .map(UserRoleDO::getRoleId)
                .toList();
    }

    @Override
    public void replaceUserRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            return;
        }
        deleteByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            UserRoleDO relation = new UserRoleDO();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    @Override
    public void deleteByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        userRoleMapper.delete(
                Wrappers.<UserRoleDO>lambdaQuery()
                        .eq(UserRoleDO::getUserId, userId));
    }

    @Override
    public void deleteByRoleId(Long roleId) {
        if (roleId == null) {
            return;
        }
        userRoleMapper.delete(
                Wrappers.<UserRoleDO>lambdaQuery()
                        .eq(UserRoleDO::getRoleId, roleId));
    }
}
