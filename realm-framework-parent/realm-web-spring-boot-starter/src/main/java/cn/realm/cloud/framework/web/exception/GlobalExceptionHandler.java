package cn.realm.cloud.framework.web.exception;

import cn.realm.cloud.framework.core.exception.BusinessException;
import cn.realm.cloud.framework.core.exception.SystemException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>只负责“异常 → ProblemDetail”的转换，HTTP 状态由
 * {@link ErrorCodeHttpStatusMapping} 通过 {@link ProblemDetailAssembler} 提供。
 *
 * <p><b>校验异常的 HTTP 状态：</b>绑定错误、参数校验失败在语义上固定是
 * 400 Bad Request，直接传入 {@link HttpStatus#BAD_REQUEST}，不依赖
 * {@code BindException#getStatusCode()}——该方法在部分 Spring 版本中
 * 不存在（只有 {@link MethodArgumentNotValidException} 通过
 * {@code ErrorResponse} 接口暴露）。这样写不依赖版本差异，语义也更明确。
 *
 * @author QI Guang
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ProblemDetailAssembler assembler;

    public GlobalExceptionHandler(ProblemDetailAssembler assembler) {
        this.assembler = assembler;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e, HttpServletRequest request) {
        return assembler.fromBusiness(e, request);
    }

    @ExceptionHandler(SystemException.class)
    public ProblemDetail handleSystem(SystemException e, HttpServletRequest request) {
        return assembler.fromSystem(e, request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e,
                                                   HttpServletRequest request) {
        return assembler.fromConstraintViolation(e, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                      HttpServletRequest request) {
        return assembler.fromValidation(e.getBindingResult(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBind(BindException e, HttpServletRequest request) {
        return assembler.fromValidation(e.getBindingResult(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Throwable.class)
    public ProblemDetail handleUnexpected(Throwable e, HttpServletRequest request) {
        return assembler.fromUnexpected(e, request, null);
    }
}
