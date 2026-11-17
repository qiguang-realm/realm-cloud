package cn.realm.cloud.framework.web.datacontract;

import jakarta.servlet.http.HttpServletRequest;

/**
 * API 版本提取策略 SPI。
 *
 * @author QI Guang
 */
public interface VersionResolver {

    /**
     * 从请求中提取版本号。
     *
     * @param request 当前请求
     * @return 版本号（如 "1" 或 "v1"），如果不存在则返回 null
     */
    String resolve(HttpServletRequest request);

    /**
     * 默认实现：从请求头提取。
     */
    class HeaderVersionResolver implements VersionResolver {
        private final String headerName;

        public HeaderVersionResolver(String headerName) {
            this.headerName = headerName;
        }

        @Override
        public String resolve(HttpServletRequest request) {
            return request.getHeader(headerName);
        }
    }
}
