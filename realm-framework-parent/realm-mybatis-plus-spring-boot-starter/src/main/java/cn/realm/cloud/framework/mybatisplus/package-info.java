/**
 * Realm 框架的调度基础设施抽象与治理自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 定义调度引擎的统一 SPI 与执行器生命周期契约，不绑定任何具体调度引擎。
 * Quartz、XXL-JOB 等引擎以 {@code optional} 依赖或独立适配器的形式接入，
 * 核心只关心“任务如何被注册、触发、治理与观测”，不关心“由谁触发”。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>核心零引擎依赖</b>：{@code core} 包禁止 import 任何 Quartz、XXL-JOB 的类型。
 *         引擎适配器必须放在 {@code engine} 子包下，并通过 {@code @ConditionalOnClass} 条件化。</li>
 *     <li><b>执行器生命周期可治理</b>：所有执行器必须通过 {@code lifecycle} 包统一管理启动与优雅关闭，
 *         禁止在业务代码中直接创建无人管理的 {@code ScheduledExecutorService}。</li>
 *     <li><b>不重复 {@code @Scheduled} 基础能力</b>：Spring 内建的 {@code @Scheduled} 与
 *         {@code TaskScheduler} 由 {@code SpringSchedulingEngine} 桥接复用，不另起一套注解体系。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Defines a unified SPI for scheduling engines and a lifecycle contract for executors,
 * without binding to any specific engine. Quartz and XXL-JOB are wired as optional
 * adapters under {@code engine/}, conditionally assembled based on classpath presence.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.mybatisplus;
