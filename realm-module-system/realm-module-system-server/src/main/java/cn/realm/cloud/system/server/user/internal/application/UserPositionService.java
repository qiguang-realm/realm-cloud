package cn.realm.cloud.system.server.user.internal.application;

import java.util.List;

/**
 * 用户-岗位分配应用服务。
 *
 * <p>管理 {@code iam_user_position} 关联表：表达用户担任了哪些岗位。
 * 关联表归属 {@code user} 模块——用户在系统里“拥有”哪些岗位，本质上是
 * 用户画像的一部分，用户管理界面是这类关联写入的主要入口。
 *
 * <p>岗位的合法性通过 {@code OrganizationFacade.existsPositions} 校验，
 * 不直接依赖 {@code organization} 的 {@code internal}。岗位被删除后的
 * 残留关联由 {@code PositionDeletedListener} 监听 {@code PositionDeletedEvent}
 * 清理，保证最终一致。
 *
 * <p>写操作由 {@code UserAdminController} 的 {@code PUT /{userId}/positions}
 * 端点驱动；不存在独立的 {@code UserPositionController}——关联表没有独立于
 * 用户的业务生命周期。
 *
 * @author QI Guang
 */
public interface UserPositionService {

    /**
     * 查询用户已分配的岗位 ID 列表。
     *
     * @param userId 用户 ID；为 {@code null} 时返回空列表
     * @return 岗位 ID 列表；无关联时返回空列表
     */
    List<Long> findPositionIdsByUserId(Long userId);

    /**
     * 全量替换用户的岗位分配。
     *
     * <p>写入前校验两件事：用户存在、{@code positionIds} 是否<b>全部</b>有效
     * （未删除的岗位）。{@code OrganizationFacade.existsPositions} 的语义是
     * “全部存在”，不是“存在任意一个”——后者会让 {@code [有效ID, 无效ID]}
     * 通过校验，无效的 {@code positionId} 被写入关联表。
     *
     * <p>“先删后插”在事务内完成，避免删除成功、插入失败留下用户没有任何
     * 岗位的中间状态。
     *
     * @param userId      用户 ID
     * @param positionIds 岗位 ID 列表；为 {@code null} 或空列表时清空该用户的所有岗位
     * @throws IllegalArgumentException 用户不存在、岗位 ID 无效
     */
    void replaceUserPositions(Long userId, List<Long> positionIds);
}
