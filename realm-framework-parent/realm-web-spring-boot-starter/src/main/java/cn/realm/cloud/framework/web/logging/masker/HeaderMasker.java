package cn.realm.cloud.framework.web.logging.masker;

import cn.realm.cloud.framework.web.properties.ObservabilityProperties;

import java.util.Set;

/**
 * HTTP Header 敏感头脱敏器。
 *
 * <p><b>职责：</b>对 {@code Authorization}、租户密钥、Cookie 等敏感头进行掩码。
 *
 * <p><b>安全依据：</b>OWASP ASVS V16 要求，如果会话令牌进入日志，必须以
 * 不可逆的哈希形式或完全掩码的形式记录，绝不允许明文记录。
 *
 * @author QI Guang
 */
public class HeaderMasker implements DataMasker {

    private final ObservabilityProperties.Logging properties;

    public HeaderMasker(ObservabilityProperties.Logging properties) {
        this.properties = properties;
    }

    @Override
    public Carrier carrier() {
        return Carrier.HEADER;
    }

    @Override
    public String mask(String key, String value) {
        if (key == null || value == null) {
            return value;
        }
        Set<String> sensitiveHeaders = properties.getSensitiveHeaders();
        if (sensitiveHeaders.contains(key.toLowerCase())) {
            // 对于凭证类，直接掩码（也可以选择保留前 4 位方便排查，如 Bearer abc...）
            return "***";
        }
        return value;
    }
}
