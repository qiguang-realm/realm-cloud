package cn.realm.cloud.framework.web.logging.masker;

import cn.realm.cloud.framework.web.properties.ObservabilityProperties;

import java.util.Set;

/**
 * URL Query 敏感参数脱敏器。
 *
 * <p><b>职责：</b>对 URL 查询参数中的敏感键（如 {@code ?token=...}, {@code ?idCard=...}）进行掩码。
 *
 * <p><b>安全依据：</b>敏感数据如果暴露在 Query String 中，会通过 Web 服务器访问日志、
 * 浏览器历史记录、Referer 头等多种渠道泄漏。日志系统必须主动剥离。
 *
 * @author QI Guang
 */
public class QueryParamMasker implements DataMasker {

    private final ObservabilityProperties.Logging properties;

    public QueryParamMasker(ObservabilityProperties.Logging properties) {
        this.properties = properties;
    }

    @Override
    public Carrier carrier() {
        return Carrier.QUERY;
    }

    @Override
    public String mask(String key, String value) {
        if (key == null || value == null) {
            return value;
        }
        Set<String> sensitiveParams = properties.getSensitiveQueryParams();
        if (sensitiveParams.contains(key.toLowerCase())) {
            return "***";
        }
        return value;
    }
}
