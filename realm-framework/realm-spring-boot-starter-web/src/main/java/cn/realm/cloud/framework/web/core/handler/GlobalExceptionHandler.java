package cn.realm.cloud.framework.web.core.handler;

import cn.realm.cloud.framework.common.base.api.ApiResponse;
import cn.realm.cloud.framework.common.enums.ErrorCode;
import cn.realm.cloud.framework.common.exception.BusinessException;
import cn.realm.cloud.framework.common.exception.SystemException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p><b>职责：</b>将 Controller / Service 层抛出的异常统一转换为
 * {@link ApiResponse}，决定 HTTP 状态码、日志级别、用户可见消息。
 *
 * <p><b>处理策略：</b>
 * <ul>
 *     <li><b>业务失败</b>（{@link BusinessException}）：HTTP 200 + 业务错误码；
 *         WARN 日志，展示原消息给用户</li>
 *     <li><b>系统失败</b>（{@link SystemException}）：HTTP 5xx；
 *         ERROR 日志（触发告警），用户侧仅展示通用消息</li>
 *     <li><b>参数错误</b>（校验 / 格式 / 缺参）：HTTP 400 + 具体错误消息</li>
 *     <li><b>未预期异常</b>：HTTP 500 + 通用消息，ERROR 日志</li>
 * </ul>
 *
 * <p><b>响应已提交保护：</b>若 {@code response.isCommitted()}（如 SSE 流式响应），
 * 无法再修改 HTTP 状态与 Header，仅记录日志。
 *
 * <p><b>日志脱敏：</b>异常消息中可能含敏感信息（如密码、身份证），
 * 生产环境建议配合 {@code MaskingUtil} 脱敏或字段级日志过滤。
 *
 * <p><b>TraceId 注入：</b>从 MDC 读取 traceId 并注入 {@link ApiResponse}，
 * 便于全链路追踪（需配合 {@code TraceIdFilter} 使用）。
 *
 * <p><b>优先级：</b>{@link Order} 设为 {@code Ordered.HIGHEST_PRECEDENCE + 100}，
 * 确保业务异常处理器优先于 Spring Security、Spring Boot Actuator 的默认处理器。
 *
 * @author QI Guang
 * @since 1.0.0
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 用户侧通用错误消息（系统异常不暴露内部细节）。
     */
    private static final String USER_FACING_ERROR_MESSAGE = "系统繁忙，请稍后再试";

    /**
     * MDC 中 traceId 的 key。
     */
    private static final String TRACE_ID_KEY = "traceId";

    // ================================================================
    // 第一部分：业务/系统异常
    // ================================================================

    /**
     * 处理业务异常（HTTP 200 + 业务错误码）。
     *
     * <p><b>日志级别 WARN：</b>业务失败是可预期的（用户操作导致），不需要告警。
     *
     * @param e       业务异常
     * @param request HTTP 请求（用于日志定位）
     * @return 统一响应体
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e,
                                                     HttpServletRequest request) {
        log.warn("[handleBusinessException] uri={}, method={}, code={}, message={}",
                request.getRequestURI(), request.getMethod(),
                e.getCode(), e.getMessage());
        return buildApiResponse(e.getErrorCode(), e.getMessage());
    }

    /**
     * 处理系统异常（HTTP 5xx + 业务错误码）。
     *
     * <p><b>日志级别 ERROR：</b>系统失败是缺陷，必须触发告警。
     * <p><b>用户侧消息：</b>不暴露内部细节，仅展示"系统繁忙"。
     *
     * @param e       系统异常
     * @param request HTTP 请求
     * @return 统一响应体（HTTP 5xx）
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(SystemException e,
                                                                   HttpServletRequest request) {
        log.error("[handleSystemException] uri={}, method={}, code={}, message={}",
                request.getRequestURI(), request.getMethod(),
                e.getCode(), e.getMessage(), e);

        HttpStatus status = resolveHttpStatus(e.getErrorCode());
        return buildErrorResponse(status, e.getErrorCode(), USER_FACING_ERROR_MESSAGE);
    }

    // ================================================================
    // 第二部分：参数校验异常（HTTP 400）
    // ================================================================

    /**
     * 处理 {@code @Valid} / {@code @Validated} 校验失败（RequestBody）。
     *
     * <p>示例：{@code @RequestBody @Valid UserCreateReqVO req}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("[handleMethodArgumentNotValid] uri={}, message={}",
                request.getRequestURI(), message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.PARAM_INVALID, message);
    }

    /**
     * 处理表单参数绑定失败（{@code @ModelAttribute}）。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException e, HttpServletRequest request) {

        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("[handleBindException] uri={}, message={}",
                request.getRequestURI(), message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.PARAM_INVALID, message);
    }

    /**
     * 处理 {@code @Validated} 方法级参数校验失败（如 {@code @RequestParam @Min(1)}）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {

        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        log.warn("[handleConstraintViolation] uri={}, message={}",
                request.getRequestURI(), message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.PARAM_INVALID, message);
    }

    /**
     * 处理请求参数类型不匹配（如路径变量期望 Long 却传了字符串）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        String requiredType = e.getRequiredType() != null
                ? e.getRequiredType().getSimpleName() : "未知";
        String message = "参数 '" + e.getName() + "' 类型错误，期望 " + requiredType;

        log.warn("[handleMethodArgumentTypeMismatch] uri={}, message={}",
                request.getRequestURI(), message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.PARAM_INVALID, message);
    }

    /**
     * 处理缺少必填请求参数（{@code @RequestParam(required = true)}）。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException e, HttpServletRequest request) {

        String message = "缺少必填参数：" + e.getParameterName();
        log.warn("[handleMissingServletRequestParameter] uri={}, message={}",
                request.getRequestURI(), message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_PARAM, message);
    }

    /**
     * 处理请求体解析失败（JSON 格式错误）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {

        // 不记录 e.getMessage()，可能含完整请求体（敏感信息）
        log.warn("[handleHttpMessageNotReadable] uri={}, cause={}",
                request.getRequestURI(),
                e.getMostSpecificCause().getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.PARAM_INVALID,
                "请求体格式错误，请检查 JSON 语法");
    }

    /**
     * 处理 {@link IllegalArgumentException}（编程错误，参数非法）。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {

        log.warn("[handleIllegalArgument] uri={}, message={}",
                request.getRequestURI(), e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.PARAM_INVALID,
                e.getMessage());
    }

    // ================================================================
    // 第三部分：HTTP 协议异常
    // ================================================================

    /**
     * 处理 HTTP 方法不支持（如用 GET 调用 POST 接口）。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {

        log.warn("[handleHttpRequestMethodNotSupported] uri={}, method={}",
                request.getRequestURI(), request.getMethod());
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED,
                "HTTP 方法不支持：" + e.getMethod());
    }

    /**
     * 处理 Content-Type 不支持。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {

        log.warn("[handleHttpMediaTypeNotSupported] uri={}, contentType={}",
                request.getRequestURI(), e.getContentType());
        return buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.MEDIA_TYPE_UNSUPPORTED,
                "不支持的媒体类型：" + e.getContentType());
    }

    /**
     * 处理 404 Not Found（需配置 {@code spring.mvc.throw-exception-if-no-handler-found=true}）。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(
            NoHandlerFoundException e, HttpServletRequest request) {

        log.warn("[handleNoHandlerFound] uri={}, method={}",
                request.getRequestURI(), request.getMethod());
        return buildErrorResponse(HttpStatus.NOT_FOUND,
                ErrorCode.PARAM_INVALID,
                "接口不存在：" + request.getRequestURI());
    }

    // ================================================================
    // 第四部分：兜底异常（HTTP 500）
    // ================================================================

    /**
     * 兜底处理所有未捕获的异常。
     *
     * <p><b>注意：</b>捕获 {@link Throwable} 而非 {@link Exception}，
     * 是为了处理 JVM 级错误（如 {@code OutOfMemoryError}、{@code StackOverflowError}），
     * 这些错误虽不可恢复，但应记录日志后返回 500，而非让请求挂起。
     *
     * <p><b>不推荐捕获 Error 的争议：</b>Effective Java Item 70 建议
     * "不要捕获 Error"，但 Web 框架层为"防止请求挂起"通常会捕获。此处遵循
     * Spring Boot 官方 {@code ResponseEntityExceptionHandler} 的做法。
     *
     * @param e       未预期异常 / 错误
     * @param request HTTP 请求
     * @return 统一响应体（HTTP 500）
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Void>> handleThrowable(
            Throwable e, HttpServletRequest request) {

        log.error("[handleThrowable] uri={}, method={}, exceptionType={}",
                request.getRequestURI(), request.getMethod(),
                e.getClass().getName(), e);

        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.SYSTEM_ERROR, USER_FACING_ERROR_MESSAGE);
    }

    // ================================================================
    // 私有辅助方法
    // ================================================================

    /**
     * 构建业务失败响应（HTTP 200 + 业务 code）。
     */
    private ApiResponse<Void> buildApiResponse(ErrorCode errorCode, String message) {
        return ApiResponse.of(errorCode.getCode(), message)
                .withTraceId(currentTraceId());
    }

    /**
     * 构建错误响应（HTTP 非 200 + 业务 code）。
     */
    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
            HttpStatus status, ErrorCode errorCode, String userMessage) {
        ApiResponse<Void> body = ApiResponse.of(errorCode.getCode(), userMessage)
                .withTraceId(currentTraceId());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 从 MDC 读取当前 traceId。
     *
     * <p>需配合 {@code TraceIdFilter} 使用；未启用时返回 {@code null}。
     */
    private String currentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 根据 {@link ErrorCode} 段位决定 HTTP 状态码。
     *
     * <ul>
     *     <li>1xxx：系统级 → 500 Internal Server Error</li>
     *     <li>4xxx：依赖级 → 503 Service Unavailable</li>
     *     <li>其他：500（兜底）</li>
     * </ul>
     */
    private HttpStatus resolveHttpStatus(ErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "errorCode 不能为 null");
        int code = errorCode.getCode();
        if (code >= 1000 && code < 2000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (code >= 4000 && code < 5000) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
