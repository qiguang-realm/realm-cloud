package cn.realm.cloud.framework.web.config;

import cn.realm.cloud.framework.web.core.handler.GlobalExceptionHandler;
import cn.realm.cloud.framework.web.core.handler.GlobalResponseBodyAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Web 层自动配置。
 *
 * <p><b>职责：</b>注册 Web 层与追踪实现无关的通用组件。
 *
 * <p><b>注册的组件：</b>
 * <ul>
 *     <li>{@link GlobalExceptionHandler}：全局异常处理</li>
 *     <li>{@link GlobalResponseBodyAdvice}：响应体自动包装</li>
 * </ul>
 *
 * <p><b>Trace Context 过滤器去哪了？</b>
 * {@code TraceContextFilter} 依赖 {@code OpenTelemetry} Bean，
 * 装配条件与 Web 层通用组件不同，已拆分到
 * {@link TraceContextFilterAutoConfiguration}，见该类文档。
 *
 * @author QI Guang
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebAutoConfiguration.class);

    /**
     * 注册 {@link GlobalExceptionHandler}。
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        log.info("[globalExceptionHandler] 注册全局异常处理器");
        return new GlobalExceptionHandler();
    }

    /**
     * 注册 {@link GlobalResponseBodyAdvice}。
     */
    @Bean
    @ConditionalOnMissingBean(GlobalResponseBodyAdvice.class)
    public GlobalResponseBodyAdvice globalResponseBodyAdvice() {
        log.info("[globalResponseBodyAdvice] 注册全局响应体包装器");
        return new GlobalResponseBodyAdvice();
    }
}
