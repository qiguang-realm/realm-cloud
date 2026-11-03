package cn.realm.cloud.framework.security.core.risk;

/**
 * 风险评估器 SPI。
 *
 * <p>实现方可以是基于规则的简单实现，也可以接入风控引擎。
 * starter 提供默认的 {@code SimpleRuleRiskEvaluator}。
 *
 * @author QI Guang
 */
public interface RiskEvaluator {

    /**
     * 评估风险。
     *
     * @param accountId 主账号 ID（可为 null，表示尚未认证）
     * @param context   情境信号
     * @return 风险评分
     */
    RiskScore evaluate(Long accountId, RiskContext context);
}
