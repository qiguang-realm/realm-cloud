package cn.realm.cloud.framework.security.config;

import cn.realm.cloud.framework.security.config.properties.RealmSecurityProperties;
import cn.realm.cloud.framework.security.core.LoginUserProvider;
import cn.realm.cloud.framework.security.core.TokenService;
import cn.realm.cloud.framework.security.web.filter.TokenAuthenticationFilter;
import cn.realm.cloud.framework.security.web.handler.RestAccessDeniedHandler;
import cn.realm.cloud.framework.security.web.handler.RestAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全过滤器链配置。
 *
 * <p>依据：OAuth 2.1 / RFC 9700——资源服务器使用 Bearer Token；
 * 默认拒绝（deny by default）是 OWASP 的基本原则。
 *
 * <p><b>放行规则分两类：</b>
 * <ul>
 *   <li>{@code realm.security.permit-all-paths}：全局放行路径（如 /error、/actuator/health）</li>
 *   <li>{@code realm.security.auth.permit-all}：相对于 {@code auth.path} 的登录流程子路径
 *       （如 login、captcha、refresh、oauth2/**、passkey/authenticate/**、mfa/**）。
 *       <b>不放行 profile 和 passkey/register/**</b>——这两个端点需要已认证的会话</li>
 * </ul>
 *
 * <p><b>关于 {@code @ConditionalOnMissingBean}：</b>业务方如果定义了自己的
 * {@code SecurityFilterChain}，本配置会整体让位——包括登录放行规则在内全部由业务方接管。
 * 如果业务方需要保留 starter 的登录放行、同时只覆盖部分路径，应显式配置
 * {@code permit-all-paths} 和 {@code auth.permit-all}，而不是替换 FilterChain。
 *
 * @author QI Guang
 */
@Configuration(proxyBeanMethods = false)
public class SecurityFilterChainConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain realmSecurityFilterChain(
            HttpSecurity http,
            TokenService tokenService,
            LoginUserProvider loginUserProvider,
            RealmSecurityProperties properties) throws Exception {

        // TokenAuthenticationFilter 不注册为 Bean：如果它是 Bean，Spring Boot 会把它
        // 额外注册到 Servlet 容器，导致过滤器执行两次（Security FilterChain 内外各一次）。
        TokenAuthenticationFilter tokenFilter =
                new TokenAuthenticationFilter(tokenService, loginUserProvider);

        // 拼接登录流程的放行路径
        String authPath = normalizePath(properties.getAuthPath());
        List<String> authPermitPaths = new ArrayList<>();
        for (String sub : properties.getAuthPermitAll()) {
            authPermitPaths.add(joinPath(authPath, sub));
        }

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .anonymous(Customizer.withDefaults())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    // 全局放行路径：/error、/actuator/health 等
                    properties.getPermitAllPaths().forEach(p ->
                            auth.requestMatchers(p).permitAll());
                    // 登录流程放行路径：/auth/login、/auth/captcha、/auth/oauth2/** 等
                    if (!authPermitPaths.isEmpty()) {
                        auth.requestMatchers(authPermitPaths.toArray(String[]::new)).permitAll();
                    }
                    // 其余全部需要认证（默认拒绝）
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 确保路径以 "/" 开头和结尾（用于拼接子路径）。
     */
    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/auth/";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return p.endsWith("/") ? p : p + "/";
    }

    /**
     * 拼接父路径与子路径，处理子路径带斜杠的边界情况。
     */
    private String joinPath(String base, String sub) {
        if (sub == null || sub.isBlank()) {
            return base + "**";
        }
        String s = sub.startsWith("/") ? sub.substring(1) : sub;
        return base + s;
    }
}
