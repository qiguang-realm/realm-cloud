package cn.realm.cloud.framework.web.logging.masker;

import cn.realm.cloud.framework.web.properties.ObservabilityProperties;

import java.util.Set;

/**
 * Body 敏感字段脱敏器。
 *
 * <p><b>职责：</b>对 JSON 或表单 Body 中的敏感字段（如密码、身份证、卡号）进行掩码。
 *
 * @author QI Guang
 */
public class BodyFieldMasker implements DataMasker {

    private final ObservabilityProperties.Logging properties;

    public BodyFieldMasker(ObservabilityProperties.Logging properties) {
        this.properties = properties;
    }

    @Override
    public Carrier carrier() {
        return Carrier.BODY;
    }

    @Override
    public String mask(String key, String value) {
        if (key == null || value == null) {
            return value;
        }
        Set<String> sensitiveFields = properties.getSensitiveBodyFields();
        if (sensitiveFields.contains(key.toLowerCase())) {
            return "***";
        }
        return value;
    }
}
