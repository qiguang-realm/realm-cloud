package cn.realm.cloud.framework.web.security;

import cn.realm.cloud.framework.web.util.WebRequestAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;

/**
 * 客户端 IP 及客户端标识解析器（安全优先）。
 *
 * <p><b>安全背景（OWASP ASVS 5.0 及 OWASP Cheat Sheet）：</b>
 * 攻击者可以在请求中直接伪造 {@code X-Forwarded-For} 或 {@code X-Real-IP} 头。
 * 只有在确认请求来自“可信代理”（如内部 Nginx、API 网关）时，才能解析这些转发头。
 *
 * <p><b>客户端标识：</b>除了单独的 IP 解析外，本类还提供 {@link #resolveClientId(HttpServletRequest)}
 * 用于构建高并发防刷、验证码风控等场景所需的 {@code IP|UA} 唯一标识。
 *
 * @author QI Guang
 */
public class ClientIpResolver {

    /**
     * User-Agent 的最大保留长度。
     * 防止恶意请求发送数 KB 的 UA 导致 Redis Key 过长、OOM 或数据库索引失效。
     */
    private static final int MAX_USER_AGENT_LENGTH = 128;

    /**
     * 可信代理的 IP 集合（如内部 Nginx 节点的 IP）。
     */
    private final Set<String> trustedProxies;

    public ClientIpResolver(Set<String> trustedProxies) {
        this.trustedProxies = trustedProxies != null ? trustedProxies : Set.of();
    }

    // ================================================================
    // IP 解析
    // ================================================================

    /**
     * 从当前请求上下文解析客户端真实 IP。
     *
     * @return 客户端 IP；如果当前线程没有请求，返回 {@code "unknown"}
     */
    public String resolve() {
        HttpServletRequest request = WebRequestAccessor.getRequest();
        return request != null ? resolve(request) : "unknown";
    }

    /**
     * 从指定请求中解析客户端真实 IP。
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return 客户端真实 IP
     */
    public String resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // 1. 获取不可伪造的直连 IP
        String remoteAddr = request.getRemoteAddr();

        // 2. 如果直连 IP 不是来自可信代理，直接返回（防御 IP 伪造）
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // 3. 只有来自可信代理时，才解析转发头
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor) && !"unknown".equalsIgnoreCase(forwardedFor)) {
            int commaIndex = forwardedFor.indexOf(',');
            String clientIp = commaIndex > 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor;
            return clientIp.trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp) && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }

        return remoteAddr;
    }

    // ================================================================
    // 客户端标识构建 (IP + UA)
    // ================================================================

    /**
     * 从当前请求上下文构建客户端唯一标识。
     *
     * @return 格式为 {@code ip|ua} 的字符串；如果当前线程没有请求，返回 {@code "unknown|unknown"}
     */
    public String resolveClientId() {
        HttpServletRequest request = WebRequestAccessor.getRequest();
        return request != null ? resolveClientId(request) : "unknown|unknown";
    }

    /**
     * 从指定请求构建客户端唯一标识（IP + User-Agent）。
     *
     * <p><b>格式：</b>{@code ip|ua}
     * <p><b>清洗规则：</b>
     * <ul>
     *     <li>IP 通过 {@link #resolve(HttpServletRequest)} 安全获取，防伪造。</li>
     *     <li>UA 为空时替换为 {@code "unknown"}，过长时截断至 128 字符，防止 OOM 或缓存穿透。</li>
     * </ul>
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return 客户端标识字符串，非 null
     */
    public String resolveClientId(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String ip = resolve(request);

        String ua = request.getHeader("User-Agent");
        if (!StringUtils.hasText(ua)) {
            ua = "unknown";
        } else if (ua.length() > MAX_USER_AGENT_LENGTH) {
            ua = ua.substring(0, MAX_USER_AGENT_LENGTH);
        }

        return ip + "|" + ua;
    }

    /**
     * 判断给定 IP 是否为可信代理。
     */
    private boolean isTrustedProxy(String ip) {
        return trustedProxies.contains(ip);
    }
}
