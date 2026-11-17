package cn.realm.cloud.framework.web.security;

import cn.realm.cloud.framework.web.properties.WebSecurityProperties;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局 CORS 配置器。
 *
 * <p><b>标准依据：</b>OWASP CORS 最佳实践。
 * <ul>
 *     <li>默认拒绝：如果未配置任何白名单，不注册任何跨域映射。</li>
 *     <li>禁止危险组合：不允许 {@code allowedOrigins} 包含 {@code *} 的同时开启凭证。</li>
 *     <li>统一暴露追踪头：自动暴露 W3C Trace Context 的 {@code traceparent} 和 {@code tracestate}。</li>
 * </ul>
 *
 * @author QI Guang
 */
public class WebCorsConfigurer implements WebMvcConfigurer {

    private final WebSecurityProperties properties;

    public WebCorsConfigurer(WebSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        WebSecurityProperties.Cors cors = properties.getCors();

        // 如果没有配置允许的来源，默认拒绝所有跨域请求
        if (cors.getAllowedOrigins().isEmpty()) {
            return;
        }

        // 校验危险组合：不允许 "*" 与 allowCredentials=true 共存
        if (cors.getAllowedOrigins().contains("*") && cors.isAllowCredentials()) {
            throw new IllegalStateException("CORS 配置错误：当 allowCredentials=true 时，allowedOrigins 不能包含 '*'。请配置具体的域名白名单。");
        }

        // 自动注入追踪头，供前端读取（与 TraceContextFilter 配合）
        List<String> exposedHeaders = new ArrayList<>(cors.getExposedHeaders());
        if (!exposedHeaders.contains("traceparent")) exposedHeaders.add("traceparent");
        if (!exposedHeaders.contains("tracestate")) exposedHeaders.add("tracestate");

        registry.addMapping("/**")
                .allowedOrigins(cors.getAllowedOrigins().toArray(new String[0]))
                .allowedMethods(cors.getAllowedMethods().toArray(new String[0]))
                .allowedHeaders(cors.getAllowedHeaders().toArray(new String[0]))
                .exposedHeaders(exposedHeaders.toArray(new String[0]))
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(cors.getMaxAge());
    }
}
