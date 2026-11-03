package cn.realm.cloud.system.server.user.internal.listener;

import cn.realm.cloud.system.server.organization.events.PositionStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 岗位状态变更事件的监听器。
 *
 * <p>具体的响应行为取决于业务需求。典型场景：
 * <ul>
 *   <li>岗位停用时，撤销持有该岗位的用户在缓存中的权限</li>
 *   <li>审计模块记录岗位状态变更轨迹</li>
 * </ul>
 *
 * <p>如果当前没有明确的业务需求，<b>不要</b>为了“占位”而写一个空监听器
 * ——空监听器会让 Event Publication Registry 记录“已投递”，但实际
 * 没有任何行为发生，反而增加排查时的困惑。没有订阅方时，事件发布
 * 本身不报错，Event Publication Registry 会记录未完成投递（取决于配置），
 * 等订阅方补上后会自动处理。
 *
 * @author QI Guang
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PositionStatusChangedListener {

    @ApplicationModuleListener
    public void on(PositionStatusChangedEvent event) {
        if (event == null || event.id() == null) {
            return;
        }
        // 按业务需求实现；无需求时本监听器可以不存在
        log.debug("岗位状态变更, positionId={}, status={}",
                event.id(), event.status());
    }
}
