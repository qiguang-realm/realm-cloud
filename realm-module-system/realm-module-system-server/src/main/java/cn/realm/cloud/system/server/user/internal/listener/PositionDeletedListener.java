package cn.realm.cloud.system.server.user.internal.listener;

import cn.realm.cloud.system.server.organization.events.PositionDeletedEvent;
import cn.realm.cloud.system.server.user.internal.persistence.UserPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 岗位删除事件的监听器。
 *
 * <p>清理 {@code iam_user_position} 中指向已删除岗位的关联记录。
 * 这是 {@code organization → events → user} 单向依赖链的一部分：
 * {@code organization} 删除岗位时只发布事件，不反向查询
 * {@code iam_user_position} 表；{@code user} 模块作为该表的持有方，
 * 订阅事件后自行清理。
 *
 * <p><b>幂等性：</b>事件可能因 Event Publication Registry 的重试
 * 被投递多次，清理逻辑必须幂等——按 {@code positionId} 删除关联，
 * 重复执行的结果与执行一次相同。
 *
 * <p><b>最终一致：</b>从岗位删除提交到本监听器执行完成之间，
 * {@code iam_user_position} 中可能短暂存在悬空关联。用户岗位查询
 * 应容忍“岗位查不到”的情况。
 *
 * @author QI Guang
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PositionDeletedListener {

    private final UserPositionRepository userPositionRepository;

    @ApplicationModuleListener
    public void on(PositionDeletedEvent event) {
        if (event == null || event.id() == null) {
            return;
        }
        userPositionRepository.deleteByPositionId(event.id());
        log.info("清理用户岗位关联, positionId={}", event.id());
    }
}
