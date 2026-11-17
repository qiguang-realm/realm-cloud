/**
 * Realm 框架的配置中心客户端接入自动配置模块（基于 Spring Boot 3 ConfigData API）。
 *
 * <p><b>模块定位：</b>
 * 作为 ConfigData API 的适配与委托层，为 Nacos、Apollo、Consul 等具体配置中心客户端
 * 提供统一的 {@code ConfigClientAdapter} SPI。应用通过 {@code spring.config.import}
 * 显式声明配置来源，本模块负责将前缀（如 {@code nacos:}、{@code apollo:}）路由到
 * 对应的适配器，并补充 {@code @RefreshScope} 行为约定、敏感配置解密钩子与配置变更事件。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>不使用旧版 Bootstrap 机制</b>：禁止在 {@code src/main/resources} 中提供
 *         {@code bootstrap.yml}，禁止注册 {@code BootstrapConfiguration}。Spring Cloud
 *         2025.0.0.0 起该路径已被移除，继续使用会导致配置加载静默失效。</li>
 *     <li><b>ConfigDataLocationResolver 必须通过 spring.factories 注册</b>：
 *         {@code AutoConfiguration.imports} 只负责 {@code @AutoConfiguration} 类，
 *         {@code ConfigDataLocationResolver} 的注册入口是 {@code spring.factories}
 *         中的 {@code org.springframework.boot.context.config.ConfigDataLocationResolver} 键。
 *         两者不可混用。</li>
 *     <li><b>适配器不绑定单一配置中心</b>：{@code ConfigClientAdapter} 是唯一的
 *         SPI，Nacos、Apollo、Consul 的具体客户端以 {@code optional} 依赖引入，
 *         通过 {@code @ConditionalOnClass} 条件化接入。核心模块的 compile 依赖
 *         中不得出现任何配置中心客户端的类型。</li>
 *     <li><b>解密是 SPI，不是实现</b>：{@code SensitiveConfigDecryptor} 只定义接口，
 *         具体的解密逻辑（对接 Vault、KMS 或 Jasypt）由应用实现，本模块不硬编码
 *         任何加密算法或密钥管理方案。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Serves as an adapter and delegation layer for Spring Boot 3's ConfigData API,
 * providing a unified {@code ConfigClientAdapter} SPI for Nacos, Apollo, Consul,
 * and other config-center clients. Configuration sources are declared explicitly
 * via {@code spring.config.import}; this module routes prefixes to the appropriate
 * adapter and supplements refresh contracts, decryption hooks, and change events.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.config.client;
