package cn.realm.cloud.framework.web.autoconfigure;

import cn.realm.cloud.framework.web.exception.ErrorCodeHttpStatusMapping;
import cn.realm.cloud.framework.web.exception.GlobalExceptionHandler;
import cn.realm.cloud.framework.web.exception.ProblemDetailAssembler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * RFC 9457 错误契约自动配置。
 *
 * <p><b>职责：</b>装配“逻辑错误码 → HTTP 状态映射 → ProblemDetail 组装 → 全局异常拦截”的完整链路。
 * 这套契约严格遵循 <b>IETF RFC 9457 (Problem Details for HTTP APIs)</b> 标准。
 *
 * <p><b>模块分工：</b>
 * <ul>
 *     <li>{@link ErrorCodeHttpStatusMapping}：维护错误码到 HTTP 状态的映射表。
 *         内置框架级（1xxx/3xxx等）映射，并提供 {@code register} 方法供业务模块
 *         在启动期注册自己的业务错误码（2xxx）映射。未注册的码兜底 500。</li>
 *     <li>{@link ProblemDetailAssembler}：将异常、校验结果等转换为标准的
 *         {@code ProblemDetail}，并注入 {@code errorCode} / {@code violations} 等扩展成员。</li>
 *     <li>{@link GlobalExceptionHandler}：{@code @RestControllerAdvice} 全局拦截，
 *         委派给 Assembler 处理。</li>
 * </ul>
 *
 * <p><b>装配顺序：</b>声明 {@code after = WebSecurityAutoConfiguration.class}——
 * 确保错误响应（4xx/5xx）也能携带安全响应头（如 {@code X-Content-Type-Options}）。
 *
 * <p><b>配置开关：</b>{@code realm.web.error.enabled=false} 时整体退让。
 *
 * @author QI Guang
 * @see ErrorCodeHttpStatusMapping
 * @see ProblemDetailAssembler
 * @see GlobalExceptionHandler
 */
@AutoConfiguration(after = WebSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "realm.web.error", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ErrorContractAutoConfiguration {

    /**
     * 错误码到 HTTP 状态的映射表。
     *
     * <p>框架级错误码在类的静态块中已预置。业务模块可以通过注入此 Bean，
     * 调用 {@code register(errorCode, HttpStatus)} 来注册自己的精确映射。
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorCodeHttpStatusMapping errorCodeHttpStatusMapping() {
        return new ErrorCodeHttpStatusMapping();
    }

    /**
     * ProblemDetail 组装器。
     *
     * <p>依赖 {@link ErrorCodeHttpStatusMapping} 来获取异常对应的 HTTP 状态。
     * {@code @ConditionalOnBean} 确保映射表就绪后再创建，避免参数注入失败。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ErrorCodeHttpStatusMapping.class)
    public ProblemDetailAssembler problemDetailAssembler(ErrorCodeHttpStatusMapping statusMapping) {
        return new ProblemDetailAssembler(statusMapping);
    }

    /**
     * 全局异常处理器。
     *
     * <p>依赖 {@link ProblemDetailAssembler} 来组装最终响应体。
     * 捕获 {@code BusinessException}、{@code SystemException}、
     * 参数校验异常以及兜底的 {@code Throwable}。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ProblemDetailAssembler.class)
    public GlobalExceptionHandler globalExceptionHandler(ProblemDetailAssembler assembler) {
        return new GlobalExceptionHandler(assembler);
    }
}
