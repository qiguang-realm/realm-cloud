/**
 * Realm 框架的 API 网关（独立部署的 WebFlux 响应式应用）。
 *
 * <p><b>模块定位：</b>
 * 作为系统南北向流量的唯一入口，在请求到达后端业务服务之前执行跨领域的策略：
 * 路由分发、Token 合法性校验、粗粒度访问控制、流量治理（限流）、
 * 可观测性入口（追踪与访问日志），以及向下游传递可信的用户与租户上下文。
 * 不承载业务逻辑、不聚合数据、不做对象级授权、不访问任何业务数据库。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>WebFlux 专用，Servlet 零容忍</b>：类路径中不得出现
 *         {@code spring-boot-starter-web} 或任何 Servlet API 的 compile 依赖。
 *         引入 Servlet 栈会导致 Spring Cloud Gateway 启动时直接失败，
 *         而不是降级为阻塞模式。同理，不得引入 {@code realm-web-spring-boot-starter}
 *         或 {@code realm-authorization-spring-boot-starter}（它们基于 Servlet 栈）。</li>
 *     <li><b>事件循环线程禁止阻塞</b>：所有 {@code GlobalFilter} 的 {@code filter} 方法
 *         中禁止调用 {@code block()}、{@code toFuture().get()} 或任何同步 I/O。
 *         需要异步依赖（如 Redis、JWT 校验）时，必须使用响应式客户端
 *         （{@code ReactiveRedisTemplate}、{@code ReactiveJwtDecoder}）。
 *         OWASP 与 Spring 官方均明确：网关的事件循环线程被阻塞会拖垮整个网关的吞吐。</li>
 *     <li><b>过滤器职责单一</b>：每个 {@code GlobalFilter} 只做一类事，类名必须
 *         体现用途（{@code JwtAuthGlobalFilter}、{@code TraceContextGlobalFilter}），
 *         禁止出现 {@code AllInOneGlobalFilter} 或 {@code CommonFilter}。
 *         过滤器顺序必须通过 {@code GatewayFilterOrder} 常量集中管理，
 *         禁止在 {@code getOrder()} 中硬编码魔数。</li>
 *     <li><b>错误响应符合 RFC 9457</b>：{@code GlobalErrorWebExceptionHandler}
 *         必须输出 {@code application/problem+json}，不得沿用 Servlet 栈的
 *         {@code @RestControllerAdvice} 或 {@code ApiResponse}。网关的错误契约
 *         与后端服务的错误契约在媒体类型层面保持一致，但实现机制不同
 *         （{@code ErrorWebExceptionHandler} vs {@code ResponseEntityExceptionHandler}）。</li>
 *     <li><b>网关无状态</b>：不持有任何业务数据库连接，不定义 {@code *DO.java}。
 *         限流计数、Token 黑名单等状态依赖 Redis，但网关本身不持久化业务数据。
 *         审计与操作日志下沉到后端服务（{@code realm-audit-logging}）。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * A standalone WebFlux API gateway serving as the sole north-south traffic entry.
 * Executes cross-cutting policies (routing, token validation, coarse-grained access
 * control, rate limiting, observability) before forwarding to backend services.
 * Carries no business logic, no data aggregation, no object-level authorization,
 * and no business database access.
 *
 * @author QI Guang
 */
package cn.realm.cloud.gateway;
