/**
 * Realm 框架的 Redis 数据访问抽象自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 提供生产就绪的 {@code RedisTemplate} 序列化默认值与 {@code RedisCacheManager} 缓存配置，
 * 并将 Redisson 确立为唯一的 {@code RedisConnectionFactory}。不包含分布式锁、限流器等
 * Redisson 工具 API（归 {@code realm-stability}），也不承载业务缓存语义。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>序列化安全</b>：默认 {@code RedisTemplate} 的 value 序列化器必须注册
 *         {@code JavaTimeModule} 并禁用 {@code WRITE_DATES_AS_TIMESTAMPS}。禁止使用
 *         JDK 序列化作为默认值——它在 Redis 中不可读，且要求所有缓存类实现
 *         {@code Serializable}。</li>
 *     <li><b>连接工厂唯一性</b>：Redisson 的 {@code RedissonConnectionFactory} 与 Lettuce 的
 *         {@code LettuceConnectionFactory} 同时存在时，必须排除 Lettuce。本模块在
 *         {@code redisson-spring-boot-starter} 上排除 {@code lettuce-core} 和捆绑的
 *         {@code redisson-spring-data-xx}，并显式锁定 {@code redisson-spring-data-35}
 *         以匹配 Spring Boot 3.5.x。</li>
 *     <li><b>缓存默认值不可为“无”</b>：{@code RedisCacheManager} 的默认配置必须有
 *         明确的 TTL（而不是 Spring Boot 的“永不过期”），key 前缀必须可配置，
 *         null 值策略必须显式声明。禁止将 Spring Boot 的“工作但错误”的默认值
 *         原样传递给生产环境。</li>
 *     <li><b>不承载分布式工具</b>：{@code RLock}、{@code RRateLimiter}、{@code RMap}
 *         等 Redisson 分布式工具 API 不属于数据访问范畴，本模块不得封装或自动配置它们。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides production-ready {@code RedisTemplate} serialization defaults and
 * {@code RedisCacheManager} cache configuration, with Redisson as the sole
 * {@code RedisConnectionFactory}. Distributed tooling (locks, rate limiters)
 * belongs to {@code realm-stability}, not this module.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.data.redis;
