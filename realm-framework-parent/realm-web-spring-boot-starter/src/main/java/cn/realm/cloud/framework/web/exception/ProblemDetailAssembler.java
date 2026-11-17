package cn.realm.cloud.framework.web.exception;

import cn.realm.cloud.framework.core.exception.BusinessException;
import cn.realm.cloud.framework.core.exception.ErrorCode;
import cn.realm.cloud.framework.core.exception.FrameworkErrorCode;
import cn.realm.cloud.framework.core.exception.SystemException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link ProblemDetail} 组装器。
 *
 * <p><b>职责：</b>把异常/校验失败等“输入”翻译成 RFC 9457 {@link ProblemDetail}
 * “输出”，统一设置 {@code type}、{@code title}、{@code instance}、{@code errorCode}
 * 扩展属性。
 *
 * <p><b>HTTP 状态来源：</b>通过 {@link ErrorCodeHttpStatusMapping} 把
 * {@link ErrorCode} 映射为 {@link HttpStatus}。{@link ErrorCode} 本身
 * 不携带 HTTP 语义，映射关系由传输层维护。
 *
 * <p><b>无状态设计：</b>除构造器注入的 {@link ErrorCodeHttpStatusMapping} 外，
 * 不持有任何可变状态，可在多线程间安全共享。
 *
 * @author QI Guang
 * @see ProblemDetail
 * @see ErrorCodeHttpStatusMapping
 */
public class ProblemDetailAssembler {

    private static final String TYPE_PREFIX = "urn:realm:error:";
    private static final String DEFAULT_SYSTEM_MESSAGE = "系统繁忙，请稍后再试";
    private static final String DEFAULT_BUSINESS_MESSAGE = "请求处理失败";
    private static final String VALIDATION_FAILED_DETAIL = "请求参数校验失败";
    private static final String UNKNOWN_STATUS_TITLE = "Unknown Status";

    private final ErrorCodeHttpStatusMapping statusMapping;

    public ProblemDetailAssembler(ErrorCodeHttpStatusMapping statusMapping) {
        this.statusMapping = Objects.requireNonNull(statusMapping,
                "statusMapping must not be null");
    }

    // ================================================================
    // 业务异常
    // ================================================================

    public ProblemDetail fromBusiness(BusinessException e, HttpServletRequest request) {
        Objects.requireNonNull(e, "e must not be null");
        HttpStatus status = statusMapping.resolve(e.getErrorCode());
        String detail = isBlank(e.getMessage()) ? DEFAULT_BUSINESS_MESSAGE : e.getMessage();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        applyCommonProperties(pd, status, e.getCode(), request);
        return pd;
    }

    // ================================================================
    // 系统异常
    // ================================================================

    public ProblemDetail fromSystem(SystemException e, HttpServletRequest request,
                                    String userMessage) {
        Objects.requireNonNull(e, "e must not be null");
        HttpStatus status = statusMapping.resolve(e.getErrorCode());
        String detail = isBlank(userMessage) ? DEFAULT_SYSTEM_MESSAGE : userMessage;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        applyCommonProperties(pd, status, e.getCode(), request);
        return pd;
    }

    // ================================================================
    // 校验失败
    // ================================================================

    public ProblemDetail fromConstraintViolation(ConstraintViolationException e,
                                                 HttpServletRequest request) {
        Objects.requireNonNull(e, "e must not be null");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, VALIDATION_FAILED_DETAIL);
        applyCommonProperties(pd, HttpStatus.BAD_REQUEST,
                FrameworkErrorCode.PARAM_INVALID.getCode(), request);

        List<Map<String, String>> violations = e.getConstraintViolations().stream()
                .map(v -> Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", nullToEmpty(v.getMessage())))
                .toList();
        pd.setProperty("violations", violations);
        return pd;
    }

    public ProblemDetail fromValidation(BindingResult bindingResult, HttpStatusCode status,
                                        HttpServletRequest request) {
        Objects.requireNonNull(bindingResult, "bindingResult must not be null");
        Objects.requireNonNull(status, "status must not be null");

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, VALIDATION_FAILED_DETAIL);
        applyCommonProperties(pd, status,
                FrameworkErrorCode.PARAM_INVALID.getCode(), request);

        List<Map<String, String>> violations = new ArrayList<>();
        for (FieldError fe : bindingResult.getFieldErrors()) {
            violations.add(Map.of(
                    "field", fe.getField(),
                    "message", nullToEmpty(fe.getDefaultMessage())));
        }
        for (ObjectError oe : bindingResult.getGlobalErrors()) {
            violations.add(Map.of(
                    "field", oe.getObjectName(),
                    "message", nullToEmpty(oe.getDefaultMessage())));
        }
        pd.setProperty("violations", violations);
        return pd;
    }

    // ================================================================
    // 未预期异常
    // ================================================================

    public ProblemDetail fromUnexpected(Throwable e, HttpServletRequest request,
                                        String userMessage) {
        Objects.requireNonNull(e, "e must not be null");
        String detail = isBlank(userMessage) ? DEFAULT_SYSTEM_MESSAGE : userMessage;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, detail);
        applyCommonProperties(pd, HttpStatus.INTERNAL_SERVER_ERROR,
                FrameworkErrorCode.SYSTEM_ERROR.getCode(), request);
        return pd;
    }

    // ================================================================
    // 内部辅助
    // ================================================================

    private void applyCommonProperties(ProblemDetail pd, HttpStatusCode status,
                                       int errorCode, HttpServletRequest request) {
        pd.setType(typeUri(errorCode));
        pd.setTitle(reasonPhrase(status));
        pd.setProperty("errorCode", errorCode);
        URI instance = instance(request);
        if (instance != null) {
            pd.setInstance(instance);
        }
    }

    private URI typeUri(int code) {
        return URI.create(TYPE_PREFIX + code);
    }

    private String reasonPhrase(HttpStatusCode status) {
        if (status instanceof HttpStatus hs) {
            return hs.getReasonPhrase();
        }
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : UNKNOWN_STATUS_TITLE;
    }

    private URI instance(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
