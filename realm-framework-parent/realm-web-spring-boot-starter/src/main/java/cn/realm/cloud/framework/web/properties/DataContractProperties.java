package cn.realm.cloud.framework.web.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据契约配置（前缀 {@code realm.web.data-contract}）。
 *
 * @author QI Guang
 */
@ConfigurationProperties(prefix = "realm.web.data-contract")
public class DataContractProperties {

    private boolean enabled = true;
    private Version version = new Version();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public static class Version {
        /**
         * 默认 Header 策略的请求头名称
         */
        private String headerName = "X-API-Version";

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }
    }
}
