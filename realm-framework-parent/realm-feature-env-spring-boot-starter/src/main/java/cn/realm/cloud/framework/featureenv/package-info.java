/**
 * Realm 框架的特性环境流量染色与标签路由自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 为特性环境（Feature Environment）提供入站流量染色、全链路标签透传，以及基于
 * Spring Cloud LoadBalancer hint 机制的实例筛选。未携带标签的流量自动回落至基线实例，
 * 不阻断基线环境的正常调用。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>复用 Hint 机制，不重造实例筛选</b>：负载均衡的标签路由必须基于
 *         Spring Cloud LoadBalancer 的 {@code HintBasedServiceInstanceListSupplier}
 *         或等价的 {@code ServiceInstanceListSupplier} 装饰器实现，禁止从零编写
 *         实例列表获取与健康检查逻辑[reference:1]。</li>
 *     <li><b>双栈条件兼容</b>：入站染色过滤器必须同时提供 Servlet（{@code OncePerRequestFilter}）
 *         与 Reactive（{@code WebFilter}）实现，并通过 {@code @ConditionalOnClass}
 *         条件化，确保模块在两种 Web 栈下均可工作，且不强制引入另一栈的依赖。</li>
 *     <li><b>基线与特性环境隔离</b>：未携带标签的流量不得被特性环境实例“劫持”。
 *         Hint 未命中时必须回落到无标签的基线实例集合，这是特性环境不破坏正常业务的前提。</li>
 *     <li><b>传播与路由分离</b>：{@code propagation} 子包只定义出站传播的 SPI，
 *         具体的 HTTP 头注入由 {@code realm-openfeign} 等消费方实现，本模块不直接绑定
 *         任何 HTTP 客户端。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides inbound traffic tagging, full-chain label propagation, and instance
 * filtering via Spring Cloud LoadBalancer's hint mechanism. Traffic without a
 * feature-env label falls back to baseline instances, preserving normal routing.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.featureenv;
