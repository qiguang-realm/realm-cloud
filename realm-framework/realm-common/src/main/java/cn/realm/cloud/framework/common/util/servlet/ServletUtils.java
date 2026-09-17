package cn.realm.cloud.framework.common.util.servlet;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.realm.cloud.framework.common.util.json.JsonUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Servlet 工具类，封装常见的请求/响应操作。
 * <p>
 * 提供获取客户端 IP、User-Agent、请求头、请求体等功能，
 * 以及向响应输出 JSON 数据的方法。依赖 Spring Web 和 Hutool。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 输出 JSON 响应
 * ServletUtils.writeJSON(response, resultMap);
 *
 * // 获取当前请求
 * HttpServletRequest request = ServletUtils.getRequest();
 *
 * // 获取客户端 IP（自动处理代理头）
 * String ip = ServletUtils.getClientIP();
 *
 * // 获取请求体（需配置 CacheRequestBodyFilter 缓存）
 * String body = ServletUtils.getBody(request);
 * }</pre>
 *
 * @author QI Guang
 * @version 1.0.0
 */
public final class ServletUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServletUtils.class);

    /**
     * JSON 内容类型（UTF-8 编码）
     */
    private static final String JSON_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name();

    private ServletUtils() {
        throw new IllegalStateException("ServletUtils 工具类不允许实例化");
    }

    // ======================== 响应写入 ========================

    /**
     * 将对象序列化为 JSON 并写入 HttpServletResponse 响应流。
     * <p>
     * 自动设置 Content-Type 为 {@code application/json;charset=UTF-8}，
     * 并处理序列化异常（记录日志但不抛出，避免中断请求）。
     * </p>
     *
     * @param response HttpServletResponse（不能为 null）
     * @param object   待序列化的对象（可为 null，此时输出 "null"）
     */
    public static void writeJSON(HttpServletResponse response, Object object) {
        Objects.requireNonNull(response, "HttpServletResponse must not be null");

        // 设置响应头（必须在获取 Writer 之前）
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try {
            String json = JsonUtils.toJsonString(object);
            JakartaServletUtil.write(response, json, JSON_CONTENT_TYPE);
        } catch (Exception e) {
            LOGGER.error("Failed to write JSON response", e);
            // 不抛出异常，避免请求处理中断，但可返回错误状态码（由调用方决定）
            // 此处选择记录日志，调用方可自行处理
        }
    }

    /**
     * 将对象序列化为 JSON 并写入响应，同时支持自定义状态码。
     *
     * @param response   HttpServletResponse
     * @param object     待序列化对象
     * @param statusCode HTTP 状态码（如 200, 500 等）
     */
    public static void writeJSON(HttpServletResponse response, Object object, int statusCode) {
        response.setStatus(statusCode);
        writeJSON(response, object);
    }

    // ======================== 请求获取 ========================

    /**
     * 从请求上下文中获取当前 HttpServletRequest。
     * <p>
     * 需要 Spring 的 {@link RequestContextHolder} 支持（通常在 Web 环境中自动启用）。
     * </p>
     *
     * @return HttpServletRequest，若无法获取则返回 {@code null}
     */
    public static HttpServletRequest getRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    /**
     * 安全获取请求，若不存在则抛出异常（适用于必须依赖请求的场景）。
     *
     * @return 非 null 的 HttpServletRequest
     * @throws IllegalStateException 当无法获取请求时抛出
     */
    public static HttpServletRequest getRequestRequired() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            throw new IllegalStateException("No HttpServletRequest bound to current thread");
        }
        return request;
    }

    // ======================== 客户端信息 ========================

    /**
     * 获取客户端真实 IP（自动处理反向代理、负载均衡等）。
     * <p>
     * 依次尝试从 {@code X-Forwarded-For}, {@code X-Real-IP}, {@code Proxy-Client-IP},
     * {@code WL-Proxy-Client-IP} 等头中获取，若无则返回 {@code request.getRemoteAddr()}。
     * </p>
     *
     * @return IP 地址字符串，若无法获取则返回 {@code null}
     */
    public static String getClientIP() {
        HttpServletRequest request = getRequest();
        return request != null ? JakartaServletUtil.getClientIP(request) : null;
    }

    /**
     * 带默认值的 IP 获取方法。
     *
     * @param defaultIp 默认 IP（当无法获取时返回）
     * @return IP 地址或默认值
     */
    public static String getClientIP(String defaultIp) {
        String ip = getClientIP();
        return ip != null ? ip : defaultIp;
    }

    /**
     * 获取客户端 IP（指定请求）。
     *
     * @param request HttpServletRequest
     * @return IP 地址
     */
    public static String getClientIP(HttpServletRequest request) {
        return JakartaServletUtil.getClientIP(request);
    }

    /**
     * 获取 User-Agent 头。
     *
     * @return User-Agent 字符串，若不存在则返回空字符串（非 null）
     */
    public static String getUserAgent() {
        HttpServletRequest request = getRequest();
        return request != null ? getUserAgent(request) : "";
    }

    /**
     * 获取指定请求的 User-Agent。
     *
     * @param request HttpServletRequest
     * @return User-Agent，若不存在则返回空字符串（非 null）
     */
    public static String getUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null ? ua : "";
    }

    // ======================== 请求参数与头 ========================

    /**
     * 获取请求的所有参数（Map 形式，参数值取第一个值）。
     * <p>
     * 若参数有多个值，仅取第一个，如需全部值请使用 {@link HttpServletRequest#getParameterValues(String)}。
     * </p>
     *
     * @param request HttpServletRequest
     * @return 参数 Map（非 null，若不存在返回空 Map）
     */
    public static Map<String, String> getParamMap(HttpServletRequest request) {
        // Hutool 方法可能返回 null，但此处保证非空
        Map<String, String> map = JakartaServletUtil.getParamMap(request);
        return map != null ? map : new HashMap<>();
    }

    /**
     * 获取请求的所有头（Map 形式）。
     *
     * @param request HttpServletRequest
     * @return 头 Map（非 null，若不存在返回空 Map）
     */
    public static Map<String, String> getHeaderMap(HttpServletRequest request) {
        Map<String, String> map = JakartaServletUtil.getHeaderMap(request);
        return map != null ? map : new HashMap<>();
    }

    /**
     * 判断请求是否为 JSON 请求（基于 Content-Type 是否以 application/json 开头）。
     *
     * @param request ServletRequest
     * @return true 表示 JSON 请求
     */
    public static boolean isJsonRequest(ServletRequest request) {
        return StrUtil.startWithIgnoreCase(request.getContentType(), MediaType.APPLICATION_JSON_VALUE);
    }

    // ======================== 请求体读取 ========================

    /**
     * 获取请求体（字符串形式）。
     * <p>
     * <b>注意：</b>该方法仅对 JSON 请求有效，且必须配合 {@code CacheRequestBodyFilter} 使用，
     * 否则无法重复读取请求体，可能抛出异常。
     * </p>
     *
     * @param request HttpServletRequest
     * @return 请求体字符串，若非 JSON 请求或无法获取则返回 {@code null}
     */
    public static String getBody(HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return JakartaServletUtil.getBody(request);
        }
        return null;
    }

    /**
     * 获取请求体（字节数组形式）。
     * <p>
     * 同样依赖缓存过滤器，仅对 JSON 请求有效。
     * </p>
     *
     * @param request HttpServletRequest
     * @return 请求体字节数组，若无法获取则返回 {@code null}
     */
    public static byte[] getBodyBytes(HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return JakartaServletUtil.getBodyBytes(request);
        }
        return null;
    }

    // ======================== 已过时方法保留（兼容性） ========================
    // 若需保留旧版本方法名，可在此声明 @Deprecated 并委托新方法，但本版本直接移除重复方法，
    // 原 getClientIP(HttpServletRequest) 和 getParamMap 等已存在，无需改动。
    // 原 getBody 和 getBodyBytes 已保留。
}
