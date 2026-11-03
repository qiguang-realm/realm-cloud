package cn.realm.cloud.system.server.user.internal.persistence;

import cn.realm.cloud.system.server.user.internal.persistence.mapper.UserPositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link UserPositionRepository} 的 MyBatis-Plus 实现。
 *
 * <p>{@link UserPositionMapper} 仅在本类内部使用。关联表没有逻辑删除列。
 *
 * <p>{@code replaceUserPositions} 会对入参去重——前端重复提交同一岗位 ID 时，
 * 若表有 {@code UNIQUE(user_id, position_id)} 约束，未去重会抛
 * {@code DuplicateKeyException}；若无唯一约束，会留下重复行。
 *
 * @author QI Guang
 */
@Repository
@RequiredArgsConstructor
public class UserPositionRepositoryImpl implements UserPositionRepository {

    private final UserPositionMapper userPositionMapper;

    @Override
    public List<Long> findPositionIdsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userPositionMapper.selectList(UserPositionDO::getUserId, userId)
                .stream()
                .map(UserPositionDO::getPositionId)
                .toList();
    }

    @Override
    public void replaceUserPositions(Long userId, List<Long> positionIds) {
        if (userId == null) {
            return;
        }
        deleteByUserId(userId);
        if (positionIds == null || positionIds.isEmpty()) {
            return;
        }
        List<UserPositionDO> relations = positionIds.stream()
                .distinct()
                .map(positionId -> {
                    UserPositionDO relation = new UserPositionDO();
                    relation.setUserId(userId);
                    relation.setPositionId(positionId);
                    return relation;
                })
                .toList();
        userPositionMapper.insertBatch(relations);
    }

    @Override
    public void deleteByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        userPositionMapper.delete(UserPositionDO::getUserId, userId);
    }

    @Override
    public void deleteByPositionId(Long positionId) {
        if (positionId == null) {
            return;
        }
        userPositionMapper.delete(UserPositionDO::getPositionId, positionId);
    }
}
