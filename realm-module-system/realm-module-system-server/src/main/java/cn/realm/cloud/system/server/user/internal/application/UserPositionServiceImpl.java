package cn.realm.cloud.system.server.user.internal.application;

import cn.realm.cloud.system.server.organization.OrganizationFacade;
import cn.realm.cloud.system.server.user.internal.persistence.UserPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * {@link UserPositionService} 的默认实现。
 *
 * <p>依赖三个协作方：
 * <ul>
 *   <li>{@link UserPositionRepository}：关联表自身的读写</li>
 *   <li>{@link UserService}：确认用户存在，避免为不存在的用户写关联</li>
 *   <li>{@link OrganizationFacade}：校验岗位 ID 的合法性，不接触
 *       {@code organization} 的 {@code internal}</li>
 * </ul>
 *
 * <p><b>跨模块依赖方向：</b>本类通过 {@code OrganizationFacade.existsByIds}
 * <b>同步</b>校验岗位有效性（{@code user → organization}），
 * {@code organization} 模块通过发布 {@code PositionDeletedEvent}
 * 通知关联清理（{@code organization → events → user}）。
 * 两个方向同时存在不构成循环——{@code organization} 不反向同步依赖
 * {@code user} 的内部类型，也不查询 {@code iam_user_position} 表。
 *
 * <p><b>与 {@link UserRoleServiceImpl} 的对称性：</b>两者结构一致——
 * 校验用户存在 → 校验目标 ID 有效性 → 去重 → 全量替换。
 * 唯一的差异是目标模块不同：角色校验走 {@code RoleFacade}，
 * 岗位校验走 {@code OrganizationFacade}。
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class UserPositionServiceImpl implements UserPositionService {

    private final UserPositionRepository userPositionRepository;
    private final UserService userService;
    private final OrganizationFacade organizationFacade;

    /**
     * {@inheritDoc}
     *
     * <p>查询接口对“不存在的用户”返回空列表，而不是抛异常——
     * 前端回显时不需要为“用户刚被删除”这一边界单独处理。
     * 这与 {@code replaceUserPositions} 的严格校验不同：读操作宽容，写操作严格。
     */
    @Override
    public List<Long> findPositionIdsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userPositionRepository.findPositionIdsByUserId(userId);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现步骤：</b>
     * <ol>
     *   <li>校验用户存在（不存在时 {@code getUser} 抛异常）</li>
     *   <li>去重 {@code positionIds}，避免撞唯一索引</li>
     *   <li>校验岗位 ID 有效性（通过 {@code OrganizationFacade.existsByIds}）</li>
     *   <li>全量替换：先删后插，同一事务内完成</li>
     * </ol>
     *
     * <p><b>校验顺序的理由：</b>先校验用户（最轻的本地查询），
     * 再校验岗位 ID（跨模块调用，最重的操作），最后写入。
     */
    @Override
    @Transactional
    public void replaceUserPositions(Long userId, List<Long> positionIds) {
        // 1. 校验用户存在
        userService.getUser(userId);

        // 2. 去重；null 视为空列表
        List<Long> distinctIds = (positionIds == null || positionIds.isEmpty())
                ? List.of()
                : positionIds.stream().distinct().toList();

        // 3. 校验岗位有效性——空列表跳过（清空场景无需校验）
        if (!distinctIds.isEmpty() && !organizationFacade.existsByIds(distinctIds)) {
            throw new IllegalArgumentException("存在无效的岗位 ID: " + distinctIds);
        }

        // 4. 全量替换
        userPositionRepository.replaceUserPositions(userId, distinctIds);
        log.info("替换用户岗位分配成功, userId={}, count={}",
                userId, distinctIds.size());
    }
}
