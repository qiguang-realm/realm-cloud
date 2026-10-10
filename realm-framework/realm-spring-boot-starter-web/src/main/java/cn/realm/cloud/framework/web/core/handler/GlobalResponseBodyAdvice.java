package cn.realm.cloud.framework.web.core.handler;

import cn.realm.cloud.framework.common.base.api.ApiResponse;
import cn.realm.cloud.framework.web.core.annotation.RawResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Set;

/**
 * 全局响应体自动包装 Advice。
 *
 * <p><b>职责：</b>拦截 {@code @RestController} 的返回值，
 * 自动包装为 {@link ApiResponse}，让 Controller 只返回业务数据。
 *
 * <p><b>效果对比：</b>
 * <pre>{@code
 * // 无 Advice：Controller 手动包装
 * @GetMapping("/users/{id}")
 * public ApiResponse<UserVO> getUser(@PathVariable Long id) {
 *     return ApiResponse.success(userService.getUser(id));
 * }
 *
 * // 有 Advice：Controller 只返回业务数据
 * @GetMapping("/users/{id}")
 * public UserVO getUser(@PathVariable Long id) {
 *     return userService.getUser(id);  // 自动包装为 ApiResponse.success(...)
 * }
 * }</pre>
 *
 * <p><b>跳过包装的场景：</b>
 * <ul>
 *     <li>返回值已是 {@link ApiResponse}（避免二次包装）</li>
 *     <li>返回值是 {@link ResponseEntity}（用户已控制 HTTP 状态）</li>
 *     <li>返回值是 {@link Resource} / {@link byte[]} / {@link StreamingResponseBody}（文件下载）</li>
 *     <li>返回值是 {@link String}（避免与 {@code StringHttpMessageConverter} 冲突）</li>
 *     <li>方法或类标注 {@link RawResponse}（显式跳过）</li>
 *     <li>URL 匹配排除列表（Actuator / Swagger / 健康检查）</li>
 * </ul>
 *
 * <p><b>String 返回值的特殊处理：</b>
 * {@code StringHttpMessageConverter} 期望 body 是 {@link String}，
 * 若返回 {@link ApiResponse}，会导致 {@link ClassCastException}。
 * 因此本 Advice <b>跳过</b> {@code String} 返回值，让 Controller 自己包装。
 *
 * <p><b>线程安全：</b>本类无状态，线程安全。
 *
 * @author QI Guang
 * @see GlobalExceptionHandler
 * @see RawResponse
 * @since 1.0.0
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(GlobalResponseBodyAdvice.class);

    /**
     * MDC 中 traceId 的 key。
     */
    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 排除包装的 URL 前缀。
     *
     * <p>包含 Spring Boot Actuator、SpringDoc / Swagger、
     * 错误页、静态资源等。
     */
    private static final Set<String> EXCLUDED_PATH_PREFIXES = Set.of(
            "/actuator",           // Spring Boot Actuator
            "/v3/api-docs",        // SpringDoc OpenAPI 3
            "/swagger-ui",         // Swagger UI
            "/swagger-resources",  // Swagger 资源
            "/webjars",            // WebJars 静态资源
            "/error",              // Spring Boot 错误页
            "/favicon.ico",        // 网站图标
            "/druid"               // Druid 监控
    );

    // ================================================================
    // ResponseBodyAdvice 接口实现
    // ================================================================

    /**
     * 决定是否对当前响应应用本 Advice。
     *
     * <p><b>返回 {@code false} 的场景：</b>
     * <ul>
     *     <li>方法或类标注 {@link RawResponse}</li>
     *     <li>URL 匹配 {@link #EXCLUDED_PATH_PREFIXES}</li>
     * </ul>
     */
    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 1. 检查 @RawResponse 注解（方法级或类级）
        if (returnType.hasMethodAnnotation(RawResponse.class)
                || returnType.getContainingClass().isAnnotationPresent(RawResponse.class)) {
            return false;
        }

        // 2. 检查 URL 排除列表
        HttpServletRequest request = currentRequest();
        if (request != null && isExcludedPath(request.getRequestURI())) {
            return false;
        }

        return true;
    }

    /**
     * 对响应体进行包装。
     *
     * <p><b>不包装的场景：</b>
     * <ul>
     *     <li>{@code body == null}（无响应体，如 204）</li>
     *     <li>已是 {@link ApiResponse}（避免二次包装）</li>
     *     <li>是 {@link ResponseEntity}（用户已控制 HTTP 状态）</li>
     *     <li>是文件下载类型（{@link Resource} / {@code byte[]} / {@link StreamingResponseBody}）</li>
     *     <li>是 {@link String}（避免与 {@code StringHttpMessageConverter} 冲突）</li>
     * </ul>
     */
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // 1. 无响应体：不包装
        if (body == null) {
            return null;
        }

        // 2. 已是 ApiResponse：避免二次包装
        if (body instanceof ApiResponse<?>) {
            return body;
        }

        // 3. ResponseEntity：用户已控制 HTTP 状态，不包装
        //    注意：ResponseEntity 通常已由框架处理，此分支为防御性编程
        if (body instanceof ResponseEntity<?>) {
            return body;
        }

        // 4. 文件下载类型：不包装
        if (isFileDownloadType(body)) {
            return body;
        }

        // 5. String：避免与 StringHttpMessageConverter 冲突
        //    原因：StringHttpMessageConverter 期望 body 是 String，
        //    若返回 ApiResponse，会导致 ClassCastException
        if (body instanceof String) {
            return body;
        }

        // 6. 其他类型：包装为 ApiResponse.success(body)
        ApiResponse<Object> wrapped = ApiResponse.of(
                cn.realm.cloud.framework.common.enums.ErrorCode.SUCCESS.getCode(),
                cn.realm.cloud.framework.common.enums.ErrorCode.SUCCESS.getMessage(),
                body);

        // 7. 注入 traceId（与 GlobalExceptionHandler 一致）
        String traceId = currentTraceId();
        if (traceId != null) {
            wrapped = wrapped.withTraceId(traceId);
        }

        if (log.isDebugEnabled()) {
            log.debug("[beforeBodyWrite] wrapped body, type={}, traceId={}",
                    body.getClass().getSimpleName(), traceId);
        }

        return wrapped;
    }

    // ================================================================
    // 私有辅助方法
    // ================================================================

    /**
     * 判断当前请求 URL 是否在排除列表中。
     */
    private boolean isExcludedPath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        for (String prefix : EXCLUDED_PATH_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 body 是否为文件下载类型。
     */
    private boolean isFileDownloadType(Object body) {
        return body instanceof Resource
                || body instanceof byte[]
                || body instanceof StreamingResponseBody;
    }

    /**
     * 获取当前 HTTP 请求。
     *
     * <p>非 Web 线程或未绑定时返回 {@code null}。
     */
    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    /**
     * 从 MDC 读取当前 traceId。
     *
     * <p>需配合 {@code TraceIdFilter} 使用；未启用时返回 {@code null}。
     */
    private String currentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }
}
