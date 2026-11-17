package cn.realm.cloud.framework.web.security;

import cn.realm.cloud.framework.web.properties.WebSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 安全响应头过滤器。
 *
 * <p><b>标准依据：</b>OWASP Secure Headers Project。
 * 这些响应头是抵御 XSS、点击劫持、MIME 嗅探等常见前端攻击的第一道防线。
 *
 * @author QI Guang
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final WebSecurityProperties properties;

    public SecurityHeadersFilter(WebSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.getHeaders().isEnabled()) {
            // 防止浏览器 MIME 类型嗅探
            response.setHeader("X-Content-Type-Options", "nosniff");
            // 防止点击劫持（默认拒绝任何 iframe 嵌套）
            response.setHeader("X-Frame-Options", "DENY");
            // 控制 Referer 头，防止敏感信息泄露到第三方
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            // 显式禁用旧版浏览器中引入漏洞的 XSS Auditor
            response.setHeader("X-XSS-Protection", "0");
            // 跨域隔离策略（允许页面被跨域打开，但隔离资源）
            response.setHeader("Cross-Origin-Opener-Policy", "same-origin");

            // HSTS：仅在 HTTPS 环境下注入，防止中间人攻击
            if (request.isSecure() && properties.getHeaders().isHstsEnabled()) {
                response.setHeader("Strict-Transport-Security",
                        "max-age=" + properties.getHeaders().getHstsMaxAge() + "; includeSubDomains");
            }
        }

        filterChain.doFilter(request, response);
    }
}
