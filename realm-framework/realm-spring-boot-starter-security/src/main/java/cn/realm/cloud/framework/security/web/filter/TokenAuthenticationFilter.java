package cn.realm.cloud.framework.security.web.filter;

import cn.realm.cloud.framework.security.core.LoginUserProvider;
import cn.realm.cloud.framework.security.core.TokenService;
import cn.realm.cloud.framework.security.core.constant.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Token 认证过滤器。
 *
 * <p>依据：RFC 6750 从 Authorization: Bearer 头解析令牌。
 * 过滤器不抛异常：无效 token 仅不设置 SecurityContext，由 EntryPoint 统一返回 401，
 * 避免"无效 token 导致 500"的反模式。
 *
 * <p>ASVS V3 要求会话在认证时轮换，此处每次请求都从 token 重新加载 LoginUser，
 * 不依赖任何客户端可篡改的缓存标志。
 *
 * @author QI Guang
 */
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final LoginUserProvider loginUserProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            tokenService.parseAccessToken(token)
                    .flatMap(loginUserProvider::loadUserById)
                    .ifPresent(user -> {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user, null, List.of());
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(SecurityConstants.TOKEN_HEADER);
        if (header != null && header.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return header.substring(SecurityConstants.TOKEN_PREFIX.length()).trim();
        }
        return null;
    }
}
