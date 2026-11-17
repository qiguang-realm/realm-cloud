/**
 * Realm 框架的传输中立消息抽象与 Binder SPI 自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 定义与具体消息中间件无关的 {@code MessagePublisher} / {@code MessageHandler} 契约，
 * 以及可插拔的 {@code Binder} SPI。具体的 Broker 适配器（RocketMQ、RabbitMQ、Kafka 等）
 * 以独立模块存在，本模块不强制传递任何 Broker 客户端。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>核心零 Broker 依赖</b>：{@code core} 包禁止 import 任何 RocketMQ、RabbitMQ、
 *         Kafka 的客户端类型。适配器实现必须放在独立模块中，通过 {@code @ConditionalOnClass}
 *         条件化接入。</li>
 *     <li><b>Binder SPI 保持窄接口</b>：{@code Binder} 只定义连接输入输出到中间件的最小契约，
 *         不塞入重试、批量、顺序、事务等 Broker 特有语义。这些语义由各适配器在文档中
 *         显式声明，应用按需选择。</li>
 *     <li><b>类路径自动检测</b>：当类路径上只有一个 {@code Binder} 实现时，自动使用；
 *         存在多个时，通过配置 {@code realm.messaging.binder} 显式指定，避免歧义。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Defines transport-neutral messaging contracts and a pluggable {@code Binder} SPI.
 * Broker adapters (RocketMQ, RabbitMQ, Kafka, etc.) exist as separate modules;
 * this module forces no broker client dependency.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.messaging;
