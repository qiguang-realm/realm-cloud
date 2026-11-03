package cn.realm.cloud.framework.security.core.risk;

/**
 * 风险评分结果。
 *
 * <p>依据：NIST SP 800-207——访问决策应基于多因子动态评估，
 * 输出的是"要求达到的认证保证等级（AAL）"，而非简单的通过/拒绝。
 *
 * @param score            风险分（0-100，越高越危险）
 * @param requiredAalLevel 本次请求要求的最低 AAL 等级（1/2/3）
 * @param reason           评分依据（用于审计日志）
 * @author QI Guang
 */
public record RiskScore(
        int score,
        int requiredAalLevel,
        String reason
) {
    public static RiskScore low() {
        return new RiskScore(10, 1, "低风险");
    }

    public static RiskScore medium(int score, String reason) {
        return new RiskScore(score, 2, reason);
    }

    public static RiskScore high(int score, String reason) {
        return new RiskScore(score, 3, reason);
    }
}
