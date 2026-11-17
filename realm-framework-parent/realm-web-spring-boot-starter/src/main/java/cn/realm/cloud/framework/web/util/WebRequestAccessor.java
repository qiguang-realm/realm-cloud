package cn.realm.cloud.framework.web.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Web 请求上下文访问器。
 *
 * <p><b>定位：</b>本类只负责从 {@link RequestContextHolder} 中“读取”当前请求和响应，
 * 并提供基础元数据（User-Agent、Header、Parameter）。
 * 不负责响应写出、请求体缓存读取或客户端 IP 的安全解析。
 *
 * <p><b>为什么不叫 {@code RequestContextUtils}：</b>
 * Spring 官方在 {@code org.springframework.web.servlet.support} 包中已存在同名类
 * （用于访问 FlashMap 等），为避免 import 冲突和混淆，本类采用 {@code Accessor} 后缀，
 * 明确其“访问器”而非“通用工具”的职责。
 *
 * <p><b>线程安全：</b>所有方法均为静态无状态设计。但依赖线程绑定的
 * {@link RequestContextHolder}，仅能在处理 HTTP 请求的线程中获取到非 {@code null} 值。
 *
 * @author QI Guang
 */
public final class WebRequestAccessor {

    private WebRequestAccessor() {
        throw new UnsupportedOperationException("工具类禁止实例化");
    }

    // ======================== 请求 / 响应获取 ========================

    /**
     * 从当前线程上下文获取 {@link HttpServletRequest}。
     *
     * @return 当前请求；非 Web 线程或未绑定时返回 {@code null}
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 从当前线程上下文获取 {@link HttpServletResponse}。
     *
     * @return 当前响应；非 Web 线程或未绑定时返回 {@code null}
     */
    public static HttpServletResponse getResponse() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getResponse() : null;
    }

    /**
     * 获取当前 {@link HttpServletRequest}，不存在时抛出异常。
     *
     * @return 非 null 的请求对象
     * @throws IllegalStateException 当前线程未绑定请求时抛出
     */
    public static HttpServletRequest requireRequest() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            throw new IllegalStateException("当前线程未绑定 HttpServletRequest");
        }
        return request;
    }

    // ======================== 基础元数据 ========================

    /**
     * 获取当前请求的 User-Agent。
     *
     * @return User-Agent 字符串；当前线程无请求或头不存在时返回空字符串
     */
    public static String getUserAgent() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return "";
        }
        String ua = request.getHeader("User-Agent");
        return ua != null ? ua : "";
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

    /**
     * 获取请求的所有头（值取第一个）。
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return 头 Map；无头时返回空 Map（非 null）
     */
    public static Map<String, String> getHeaderMap(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String> headers = new java.util.HashMap<>();
        java.util.Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                headers.put(name, request.getHeader(name));
            }
        }
        return Collections.unmodifiableMap(headers);
    }

    /**
     * 获取请求的所有参数（值取第一个）。
     *
     * @param request HttpServletRequest，不允许为 {@code null}
     * @return 参数 Map；无参数时返回空 Map（非 null）
     */
    public static Map<String, String> getParamMap(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap == null || parameterMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new java.util.HashMap<>(parameterMap.size());
        parameterMap.forEach((key, values) -> {
            if (values != null && values.length > 0) {
                result.put(key, values[0]);
            }
        });
        return Collections.unmodifiableMap(result);
    }
}
