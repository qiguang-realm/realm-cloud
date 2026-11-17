/**
 * Realm 框架的运维管理模块。
 *
 * <p><b>模块定位：</b>
 * 面向运维人员（Ops/SRE）提供内部平台能力。目前主要包含定时任务的管理（CRUD、启停、日志查询）
 * 以及服务器节点状态信息的收集与展示。不包含底层调度引擎的实现，也不包含研发提效工具。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>只管理，不执行</b>：本模块的 {@code JobCommandService} 绝不直接创建线程或硬编码
 *         Quartz/XXL-JOB 的类。所有的底层调度执行必须通过 {@code realm-scheduling} 提供的
 *         SPI（如 {@code SchedulingEngine}）进行交互，保持引擎可插拔。</li>
 *     <li><b>职责分离</b>：绝不依赖 {@code realm-devtools}。运维管理（面向 SRE）与研发工具
 *         （面向开发者，如代码生成器）是两个完全不同的关注点，混在一起会导致模块臃肿。</li>
 *     <li><b>日志与元数据隔离</b>：定时任务的配置元数据（{@code infra_job}）和执行日志
 *         （{@code infra_job_log}）分离存储。任务执行日志属于大量写入的数据，必须通过异步或
 *         独立的归档策略处理，防止拖垮管理主流程。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides operations management capabilities for SREs, including scheduled job
 * management (CRUD, start/stop, logs) and server node status collection.
 * Does not implement scheduling engines; interacts via the {@code realm-scheduling} SPI.
 * Strictly isolated from developer tools ({@code realm-devtools}).
 *
 * @author QI Guang
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "realm-core",
                "realm-utils",
                "realm-web-spring-boot-starter",          // 管理后台的 REST API 基础
                "realm-audit-logging :: api",             // 任务启停等操作的审计
                "realm-scheduling :: api",                // 依赖调度引擎 SPI 进行实际的任务注册与触发
                "realm-mybatis-plus-spring-boot-starter"  // 任务元数据与日志的持久化
                // 不列 realm-devtools：运维管理与研发工具彻底解耦
        }
)
package cn.realm.cloud.platform;
