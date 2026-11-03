package cn.realm.cloud.system.server.user.internal.persistence;

import java.util.List;

/**
 * 用户-岗位关联仓储。
 *
 * <p>方法围绕“按用户操作岗位”设计，与 {@link UserRoleRepository} 同构，
 * 差异只在关联的是 {@code positionId} 而非 {@code roleId}。
 *
 * <p>关联表不使用逻辑删除和乐观锁——全量替换的语义是“物理删除旧记录、
 * 插入新记录”，历史变更靠审计日志追溯，不靠表内保留已删除行。
 *
 * @author QI Guang
 */
public interface UserPositionRepository {

    /**
     * 查询用户已分配的岗位 ID 列表。
     *
     * <p><b>顺序不保证</b>与插入顺序一致，由数据库执行计划决定。
     * 调用方如依赖顺序，应自行排序。
     *
     * @param userId 用户 ID；为 {@code null} 时返回空列表
     * @return 岗位 ID 列表；无关联时返回空列表
     */
    List<Long> findPositionIdsByUserId(Long userId);

    /**
     * 全量替换用户的岗位分配：先删除该用户的所有关联，再批量插入新记录。
     *
     * <p><b>去重语义：</b>实现必须对 {@code positionIds} 去重——
     * 前端重复提交同一岗位 ID 时，若表有 {@code UNIQUE(user_id, position_id)}
     * 约束，未去重会抛 {@code DuplicateKeyException}；若无唯一约束，
     * 会留下重复行，后续 {@link #findPositionIdsByUserId} 返回重复 ID。
     * 调用方无需自行去重。
     *
     * <p><b>事务要求：</b>调用方必须确保在事务内调用，否则删除成功、
     * 插入失败会留下用户没有任何岗位的中间状态。
     *
     * @param userId      用户 ID
     * @param positionIds 岗位 ID 列表；为 {@code null} 或空列表时只执行删除
     */
    void replaceUserPositions(Long userId, List<Long> positionIds);

    /**
     * 删除用户的所有岗位关联。
     *
     * <p><b>幂等：</b>用户没有岗位关联时调用不报错，不做任何操作。
     * 用于删除用户时的关联清理。
     *
     * @param userId 用户 ID；为 {@code null} 时不做任何操作
     */
    void deleteByUserId(Long userId);

    /**
     * 删除指定岗位的所有用户关联。
     *
     * <p><b>幂等：</b>岗位没有用户关联时调用不报错。
     * 用于 {@code PositionDeletedListener} 监听 {@code PositionDeletedEvent}
     * 时清理残留关联。
     *
     * @param positionId 岗位 ID；为 {@code null} 时不做任何操作
     */
    void deleteByPositionId(Long positionId);
}
