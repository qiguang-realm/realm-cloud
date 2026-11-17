package cn.realm.cloud.framework.web.logging;

import cn.realm.cloud.framework.web.logging.masker.DataMasker;
import cn.realm.cloud.framework.web.properties.ObservabilityProperties;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 统一脱敏入口。
 *
 * @author QI Guang
 */
public class SensitiveDataMasker {

    private final Map<DataMasker.Carrier, DataMasker> maskers = new EnumMap<>(DataMasker.Carrier.class);
    private final ObservabilityProperties.Logging properties;

    public SensitiveDataMasker(List<DataMasker> maskerList, ObservabilityProperties.Logging properties) {
        this.properties = properties;
        if (maskerList != null) {
            for (DataMasker masker : maskerList) {
                maskers.put(masker.carrier(), masker);
            }
        }
    }

    public String maskUri(String uri) {
        if (!properties.isMaskQueryParams() || uri == null || !uri.contains("?")) {
            return uri;
        }
        String[] parts = uri.split("\\?", 2);
        DataMasker masker = maskers.get(DataMasker.Carrier.QUERY);
        if (masker == null) {
            return uri;
        }

        StringBuilder maskedQuery = new StringBuilder();
        for (String pair : parts[1].split("&")) {
            String[] kv = pair.split("=", 2);
            String key = kv[0];
            String value = kv.length > 1 ? kv[1] : "";
            String masked = masker.mask(key, value);
            if (maskedQuery.length() > 0) {
                maskedQuery.append("&");
            }
            maskedQuery.append(key);
            if (kv.length > 1) {
                maskedQuery.append("=").append(masked);
            }
        }
        return parts[0] + "?" + maskedQuery;
    }

    public String maskHeader(String name, String value) {
        DataMasker masker = maskers.get(DataMasker.Carrier.HEADER);
        return masker != null ? masker.mask(name, value) : value;
    }
}
