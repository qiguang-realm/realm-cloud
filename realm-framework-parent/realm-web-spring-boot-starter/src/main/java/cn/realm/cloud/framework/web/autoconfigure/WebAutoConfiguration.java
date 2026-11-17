package cn.realm.cloud.framework.web.autoconfigure;

import cn.realm.cloud.framework.web.properties.WebProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Realm Web 横切能力的聚合入口。
 *
 * <p><b>职责：</b>本类<b>不</b>注册任何业务 Bean，只做两件事：
 * <ol>
 *     <li>注册 {@link WebProperties}（全局开关，前缀 {@code realm.web}）</li>
 *     <li>通过 {@link Import} 聚合 4 个按“关注点分离”原则拆分的子配置，
 *         并作为 {@code AutoConfiguration.imports} 中的唯一入口，
 *         让 starter 的加载路径清晰可追溯</li>
 * </ol>
 *
 * <p><b>模块化拆分依据：</b>对齐 Spring Boot 4 的模块化趋势（按关注点而非技术分层组织代码），
 * 将原细碎的子配置重组为 4 个高内聚的核心模块，每个模块对应明确的国际标准与领先实践：
 * <ul>
 *     <li>{@code WebSecurityAutoConfiguration}：安全基线。
 *         默认注入 OWASP 推荐的安全头，CORS 默认拒绝跨域。</li>
 *     <li>{@code ErrorContractAutoConfiguration}：错误契约。
 *         严格遵循 IETF RFC 9457 (Problem Details)，将逻辑错误码映射为 HTTP 状态，
 *         不使用自定义信封（Envelope）。</li>
 *     <li>{@code ObservabilityAutoConfiguration}：可观测性。
 *         遵循 W3C Trace Context Level 3 实现双向链路传播；
 *         遵循 OWASP ASVS V16 实现多载体敏感数据脱敏。</li>
 *     <li>{@code DataContractAutoConfiguration}：数据契约。
 *         对齐 Stripe 游标分页（Cursor-based Pagination）模式；
 *         提供默认的 API 版本解析策略。</li>
 * </ul>
 *
 * <p><b>为什么 imports 文件只列本类：</b>子配置通过 {@link Import} 聚合，
 * 新增/删除子能力只需改本类的 {@link Import} 列表，不动 imports 文件。
 * 这与 Spring Boot 官方 {@code DataSourceAutoConfiguration} 的聚合方式一致。
 *
 * <p><b>子配置之间的装配顺序：</b>{@link Import} 列表中的顺序仅表达意图，
 * 不保证实际装配顺序。子配置之间如果存在真实依赖，必须在子配置类上
 * 通过 {@code @AutoConfiguration(after = ...)} 显式声明：
 * <ul>
 *     <li>{@code ErrorContractAutoConfiguration} 应声明 {@code after = WebSecurityAutoConfiguration.class}
 *         ——确保错误响应（4xx/5xx）也能携带安全响应头。</li>
 *     <li>{@code ObservabilityAutoConfiguration} 内部应保证 Trace 上下文先于 Logging 就绪
 *         ——确保请求日志能正确携带 {@code traceId}。</li>
 * </ul>
 * 本类作为聚合入口，不承担子配置的顺序决策，只负责把子配置引入容器。
 *
 * <p><b>子配置的可选依赖保护：</b>子配置各自负责其可选依赖的
 * {@code @ConditionalOnClass} 保护（如对 OpenTelemetry 或 Spring Security 的条件装配）。
 * 总控只做聚合，不代替子配置判断“某个可选类是否存在”。
 *
 * <p><b>全局开关：</b>{@code realm.web.enabled=false} 时，本类不加载，
 * 所有子配置都不会被聚合，starter 整体退让。子配置各自还有独立开关
 * （如 {@code realm.web.security.enabled}、{@code realm.web.observability.enabled}），
 * 允许按能力粒度关闭。
 *
 * <p><b>仅 Servlet 环境生效：</b>{@link ConditionalOnWebApplication} 限定为
 * {@code SERVLET} 类型。响应体写出（{@code HttpServletResponse}）、
 * CORS 配置、Servlet 过滤器等能力依赖 Servlet API，在 Reactive（WebFlux）
 * 环境下不适用。若未来需要 WebFlux 支持，应新建独立的
 * {@code realm-webflux-spring-boot-starter}，而不是让本类同时适配两种栈。
 *
 * @author QI Guang
 * @see WebProperties
 * @see WebSecurityAutoConfiguration
 * @see ErrorContractAutoConfiguration
 * @see ObservabilityAutoConfiguration
 * @see DataContractAutoConfiguration
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "realm.web", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WebProperties.class)
@Import({
        // 1. 安全基线（最先加载，确保所有响应含安全头）
        WebSecurityAutoConfiguration.class,
        // 2. 错误契约（依赖安全基线设置的头）
        ErrorContractAutoConfiguration.class,
        // 3. 可观测性（内部保证 Trace 先于 Logging）
        ObservabilityAutoConfiguration.class,
        // 4. 数据契约（分页与版本化）
        DataContractAutoConfiguration.class
})
public class WebAutoConfiguration {
    // 聚合入口，不注册任何具体 Bean。
    // 子配置各自通过 @Bean 注册组件，各自通过 @EnableConfigurationProperties 注册属性类。
    // 子配置之间的真实依赖由子配置上的 @AutoConfiguration(after = ...) 显式声明，
    // 本类的 @Import 列表只负责“引入”，不保证“顺序”。
}
