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
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Servlet 工具类，封装常见的请求/响应操作。
 *
 * <p><b>能力：</b>
 * <ul>
 *     <li>响应写入：{@link #writeJSON(HttpServletResponse, Object)}</li>
 *     <li>请求获取：{@link #getRequest()}、{@link #requireRequest()}</li>
 *     <li>客户端信息：{@link #getClientIP()}、{@link #getUserAgent()}</li>
 *     <li>请求参数与头：{@link #getParamMap(HttpServletRequest)}、{@link #getHeaderMap(HttpServletRequest)}</li>
 *     <li>请求体读取：{@link #getBody(HttpServletRequest)}（需配合 {@code CacheRequestBodyFilter}）</li>
 * </ul>
 *
 * <p><b>线程安全：</b>本类所有方法均为无状态设计，线程安全。
 * 但由于依赖 {@link RequestContextHolder}，仅在处理 HTTP 请求的线程中可用。
 *
 * <p><b>依赖：</b>Spring Web、Hutool。
 *
 * <p><b>使用示例：</b>
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
 * @since 1.0.0
 */
public final class ServletUtils {

    private static final Logger log = LoggerFactory.getLogger(ServletUtils.class);

    /**
     * JSON 内容类型（UTF-8 编码）。
     *
     * <p>格式遵循 RFC 7231 §3.1.1.1：分号后跟空格，参数以 {@code key=value} 表示。
     */
    private static final String JSON_CONTENT_TYPE =
            MediaType.APPLICATION_JSON_VALUE + "; charset=" + StandardCharsets.UTF_8.name();

    private ServletUtils() {
        throw new UnsupportedOperationException(
                "ServletUtils is a static utility class and cannot be instantiated");
    }

    // ======================== 响应写入 ========================

    /**
     * 将对象序列化为 JSON 并写入响应流。
     *
     * <p>自动设置 Content-Type 为 {@code application/json; charset=UTF-8}。
     *
     * <p><b>异常策略：</b>序列化或写入失败时<b>记录日志但不抛出异常</b>，
     * 避免请求处理链中断。若需调用方感知失败，请使用
     * {@link #writeJSONOrThrow(HttpServletResponse, Object)}。
     *
     * @param response HttpServletResponse，不允许为 {@code null}
     * @param object   待序列化的对象（可为 {@code null}，序列化为 {@code null} 字面量）
     */
    public static void writeJSON(HttpServletResponse response, Object object) {
        Objects.requireNonNull(response, "response must not be null");
        try {
            writeJSONOrThrow(response, object);
        } catch (Exception e) {
            log.error("[writeJSON] failed to write JSON response", e);
        }
    }

    /**
     * 将对象序列化为 JSON 并写入响应流，失败时抛出异常。
     *
     * <p>适用于需要严格保证响应写入成功的场景（如金融交易、幂等接口）。
     *
     * @param response HttpServletResponse，不允许为 {@code null}
     * @param object   待序列化的对象
     * @throws IllegalStateException 序列化或写入失败时抛出
     */
    public static void writeJSONOrThrow(HttpServletResponse response, Object object) {
        Objects.requireNonNull(response, "response must not be null");
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            String json = JsonUtils.toJsonString(object);
            JakartaServletUtil.write(response, json, JSON_CONTENT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write JSON response", e);
        }
    }

    /**
     * 将对象序列化为 JSON 并写入响应流，同时设置 HTTP 状态码。
     *
     * @param response   HttpServletResponse，不允许为 {@code null}
     * @param object     待序列化对象
     * @param statusCode HTTP 状态码（如 200、400、500）
     */
    public static void writeJSON(HttpServletResponse response, Object object, int statusCode) {
        Objects.requireNonNull(response, "response must not be null");
        response.setStatus(statusCode);
        writeJSON(response, object);
    }

    // ======================== 请求获取 ========================

    /**
     * 从当前线程上下文获取 {@link HttpServletRequest}。
     *
     * <p>依赖 Spring 的 {@link RequestContextHolder}（Web 环境自动启用）。
     *
     * @return HttpServletRequest；非 Web 线程或未绑定时返回 {@code null}
     */
    public static HttpServletRequest getRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    /**
     * 获取当前 {@link HttpServletRequest}，不存在时抛出异常。
     *
     * <p>适用于必须依赖请求的场景（如拦截器、AOP 切面）。
     *
     * @return 非 null 的 HttpServletRequest
     * @throws IllegalStateException 当前线程未绑定请求时抛出
     */
    public static HttpServletRequest requireRequest() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            throw new IllegalStateException(
                    "No HttpServletRequest bound to current thread");
        }
        return request;
    }

    // ======================== 客户端信息 ========================

    /**
     * 获取当前请求的客户端真实 IP（自动处理反向代理头）。
     *
     * <p>依次尝试 {@code X-Forwarded-For}、{@code X-Real-IP}、
     * {@code Proxy-Client-IP}、{@code WL-Proxy-Client-IP} 等头，
     * 均不存在时返回 {@code request.getRemoteAddr()}。
     *
     * @return 客户端 IP；当前线程无请求时返回 {@code null}
     */
    public static String getClientIP() {
        HttpServletRequest request = getRequest();
        return request != null ? JakartaServletUtil.getClientIP(request) : null;
    }

    /**
     * 获取当前请求的客户端真实 IP，不存在时返回默认值。
     *
     * @param defaultIp 默认 IP
     * @return 客户端 IP 或 {@code defaultIp}
     */
    public static String getClientIP(String defaultIp) {
        String ip = getClientIP();
        return ip != null ? ip : defaultIp;
    }

    /**
     * 获取指定请求的客户端真实 IP。
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return 客户端 IP
     */
    public static String getClientIP(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return JakartaServletUtil.getClientIP(request);
    }

    /**
     * 获取当前请求的 User-Agent。
     *
     * @return User-Agent 字符串；当前线程无请求或头不存在时返回空字符串
     */
    public static String getUserAgent() {
        HttpServletRequest request = getRequest();
        return request != null ? getUserAgent(request) : "";
    }

    /**
     * 获取指定请求的 User-Agent。
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return User-Agent；头不存在时返回空字符串
     */
    public static String getUserAgent(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String ua = request.getHeader("User-Agent");
        return ua != null ? ua : "";
    }

    // ======================== 请求参数与头 ========================

    /**
     * 获取请求的所有参数（参数值取第一个）。
     *
     * <p>若某参数有多个值，仅取第一个。如需全部值，请使用
     * {@link HttpServletRequest#getParameterValues(String)}。
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return 参数 Map；无参数时返回空 Map（非 null）
     */
    public static Map<String, String> getParamMap(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String> map = JakartaServletUtil.getParamMap(request);
        return map != null ? map : Collections.emptyMap();
    }

    /**
     * 获取请求的所有头。
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return 头 Map；无头时返回空 Map（非 null）
     */
    public static Map<String, String> getHeaderMap(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String> map = JakartaServletUtil.getHeaderMap(request);
        return map != null ? map : Collections.emptyMap();
    }

    /**
     * 判断请求是否为 JSON 请求（Content-Type 以 {@code application/json} 开头）。
     *
     * @param request ServletRequest，不允许为 {@code null}
     * @return {@code true} 表示 JSON 请求
     */
    public static boolean isJsonRequest(ServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String contentType = request.getContentType();
        return StrUtil.startWithIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE);
    }

    // ======================== 请求体读取 ========================

    /**
     * 获取请求体（字符串形式）。
     *
     * <p><b>前置条件：</b>必须配合 {@code CacheRequestBodyFilter} 使用，
     * 否则请求体无法重复读取，可能抛出异常。
     *
     * <p>仅对 JSON 请求有效；非 JSON 请求返回 {@code null}。
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return 请求体字符串；非 JSON 请求返回 {@code null}
     */
    public static String getBody(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (isJsonRequest(request)) {
            return JakartaServletUtil.getBody(request);
        }
        return null;
    }

    /**
     * 获取请求体（字节数组形式）。
     *
     * <p><b>前置条件：</b>同 {@link #getBody(HttpServletRequest)}，需配合
     * {@code CacheRequestBodyFilter} 使用。
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return 请求体字节数组；非 JSON 请求返回 {@code null}
     */
    public static byte[] getBodyBytes(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (isJsonRequest(request)) {
            return JakartaServletUtil.getBodyBytes(request);
        }
        return null;
    }
}
