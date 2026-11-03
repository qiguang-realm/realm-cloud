package cn.realm.cloud.framework.security.core.risk.impl;

import cn.realm.cloud.framework.security.core.risk.RiskContext;
import cn.realm.cloud.framework.security.core.risk.RiskEvaluator;
import cn.realm.cloud.framework.security.core.risk.RiskScore;

import java.time.Instant;
import java.time.ZoneId;

/**
 * 基于规则的简单风险评分。
 *
 * <p>规则：
 * <ul>
 *   <li>管理端路径（/admin/**）→ 至少 AAL2</li>
 *   <li>非常规时段（本地时间 0-6 点）→ 风险分 +10</li>
 *   <li>未知设备 → 风险分 +15，要求 AAL2</li>
 *   <li>敏感操作路径（含 /export、/delete、/assign） → 要求 AAL2</li>
 * </ul>
 *
 * <p>生产环境建议替换为接入风控引擎的实现，通过
 * {@code @ConditionalOnMissingBean} 覆盖本类。
 *
 * @author QI Guang
 */
public class SimpleRuleRiskEvaluator implements RiskEvaluator {

    @Override
    public RiskScore evaluate(Long accountId, RiskContext context) {
        int score = 0;
        int requiredAal = 1;
        StringBuilder reason = new StringBuilder();

        String path = context.requestPath() == null ? "" : context.requestPath();

        if (path.startsWith("/admin/")) {
            score += 20;
            requiredAal = Math.max(requiredAal, 2);
            reason.append("管理端访问; ");
        }

        if (path.contains("/export") || path.contains("/delete")
                || path.contains("/assign") || path.contains("/revoke")) {
            score += 15;
            requiredAal = Math.max(requiredAal, 2);
            reason.append("敏感操作; ");
        }

        if (!context.knownDevice()) {
            score += 15;
            requiredAal = Math.max(requiredAal, 2);
            reason.append("未知设备; ");
        }

        int hour = Instant.ofEpochMilli(context.requestTime())
                .atZone(ZoneId.systemDefault())
                .getHour();
        if (hour < 6) {
            score += 10;
            reason.append("非常规时段; ");
        }

        if (score >= 60) {
            return RiskScore.high(score, reason.toString());
        }
        if (score >= 20) {
            return RiskScore.medium(score, reason.toString());
        }
        return RiskScore.low();
    }
}
