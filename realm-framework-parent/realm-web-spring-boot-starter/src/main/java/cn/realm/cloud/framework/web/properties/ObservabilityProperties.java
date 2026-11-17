package cn.realm.cloud.framework.web.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 可观测性配置（前缀 {@code realm.web.observability}）。
 *
 * @author QI Guang
 */
@ConfigurationProperties(prefix = "realm.web.observability")
public class ObservabilityProperties {

    private boolean enabled = true;
    private Trace trace = new Trace();
    private Logging logging = new Logging();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Trace getTrace() {
        return trace;
    }

    public void setTrace(Trace trace) {
        this.trace = trace;
    }

    public Logging getLogging() {
        return logging;
    }

    public void setLogging(Logging logging) {
        this.logging = logging;
    }

    public static class Trace {
        private boolean enabled = true;
        private boolean readRequestHeader = true;
        private boolean writeResponseHeader = true;
        private boolean writeMdc = true;
        private List<String> excludedPaths = new ArrayList<>(List.of(
                "/**/*.ico", "/**/*.css", "/**/*.js", "/**/*.map",
                "/**/*.png", "/**/*.jpg", "/**/*.jpeg", "/**/*.gif", "/**/*.svg",
                "/actuator/**"
        ));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isReadRequestHeader() {
            return readRequestHeader;
        }

        public void setReadRequestHeader(boolean readRequestHeader) {
            this.readRequestHeader = readRequestHeader;
        }

        public boolean isWriteResponseHeader() {
            return writeResponseHeader;
        }

        public void setWriteResponseHeader(boolean writeResponseHeader) {
            this.writeResponseHeader = writeResponseHeader;
        }

        public boolean isWriteMdc() {
            return writeMdc;
        }

        public void setWriteMdc(boolean writeMdc) {
            this.writeMdc = writeMdc;
        }

        public List<String> getExcludedPaths() {
            return excludedPaths;
        }

        public void setExcludedPaths(List<String> excludedPaths) {
            this.excludedPaths = excludedPaths;
        }
    }

    public static class Logging {
        private boolean enabled = true;
        private boolean maskQueryParams = true;
        private int maxPayloadSize = 4096;
        private Set<String> sensitiveBodyFields = new HashSet<>(Set.of("password", "idcard", "cardnumber", "secret"));
        private Set<String> sensitiveQueryParams = new HashSet<>(Set.of("token", "idcard", "password"));
        private Set<String> sensitiveHeaders = new HashSet<>(Set.of("authorization", "x-tenant-key", "cookie"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isMaskQueryParams() {
            return maskQueryParams;
        }

        public void setMaskQueryParams(boolean maskQueryParams) {
            this.maskQueryParams = maskQueryParams;
        }

        public int getMaxPayloadSize() {
            return maxPayloadSize;
        }

        public void setMaxPayloadSize(int maxPayloadSize) {
            this.maxPayloadSize = maxPayloadSize;
        }

        public Set<String> getSensitiveBodyFields() {
            return sensitiveBodyFields;
        }

        public void setSensitiveBodyFields(Set<String> sensitiveBodyFields) {
            this.sensitiveBodyFields = sensitiveBodyFields;
        }

        public Set<String> getSensitiveQueryParams() {
            return sensitiveQueryParams;
        }

        public void setSensitiveQueryParams(Set<String> sensitiveQueryParams) {
            this.sensitiveQueryParams = sensitiveQueryParams;
        }

        public Set<String> getSensitiveHeaders() {
            return sensitiveHeaders;
        }

        public void setSensitiveHeaders(Set<String> sensitiveHeaders) {
            this.sensitiveHeaders = sensitiveHeaders;
        }
    }
}
