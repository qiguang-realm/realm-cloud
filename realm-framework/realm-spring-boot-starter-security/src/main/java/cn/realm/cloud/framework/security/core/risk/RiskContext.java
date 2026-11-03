package cn.realm.cloud.framework.security.core.risk;

/**
 * 情境信号输入。
 *
 * @param clientIp          客户端 IP
 * @param userAgent         User-Agent
 * @param deviceFingerprint 设备指纹（前端计算后回传）
 * @param requestPath       当前请求路径
 * @param requestTime       请求时间（epoch millis）
 * @param knownDevice       该账号是否在此设备上登录过
 * @param geoCountry        国家
 * @param geoCity           城市
 * @author QI Guang
 */
public record RiskContext(
        String clientIp,
        String userAgent,
        String deviceFingerprint,
        String requestPath,
        long requestTime,
        boolean knownDevice,
        String geoCountry,
        String geoCity
) {
}
