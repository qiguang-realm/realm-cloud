📁 realm-scheduling-spring-boot-starter 📁 详细包结构全景

realm-scheduling-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/scheduling/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口（独立子包）
│   ├── SchedulingAutoConfiguration.java  # [总控] 全局开关与核心 Bean 装配
│   ├── SpringSchedulingAutoConfiguration.java  # [内置] Spring TaskScheduler 桥接
│   ├── QuartzSchedulingAutoConfiguration.java  # [可选] Quartz 引擎适配器装配
│   └── XxlJobSchedulingAutoConfiguration.java  # [可选] XXL-JOB 引擎适配器装配
│
├── core/                                 # 调度抽象契约（零引擎依赖）
│   ├── SchedulingEngine.java             # [SPI] 调度引擎统一抽象接口
│   ├── ScheduledTask.java                # 任务定义：名称、Cron、执行器引用
│   ├── TaskStatus.java                   # 任务状态枚举（PENDING / RUNNING / PAUSED）
│   └── SchedulingEngineProvider.java     # [SPI] 引擎发现与选择（类似 ScheduleServiceProvider）
│
├── engine/                               # 引擎适配器（出站适配器，均为 optional）
│   ├── spring/
│   │   └── SpringSchedulingEngine.java   # 基于 Spring TaskScheduler 的轻量实现
│   ├── quartz/
│   │   └── QuartzSchedulingEngine.java   # 基于 Quartz Scheduler 的适配器
│   └── xxljob/
│       └── XxlJobSchedulingEngine.java   # 基于 XXL-JOB Executor 的适配器
│
├── lifecycle/                            # 执行器生命周期治理
│   ├── ExecutorLifecycleManager.java     # 执行器启动、停止、优雅关闭
│   └── SchedulingLifecycle.java          # 实现 SmartLifecycle，绑定容器生命周期
│
├── metrics/                              # 可观测性
│   └── SchedulingMetrics.java            # 任务耗时、成功/失败计数、执行器状态
│
└── properties/                           # 配置属性类
├── SchedulingProperties.java         # 全局开关、默认引擎选择
└── EngineProperties.java             # 各引擎的细粒度配置（Cron 覆盖、线程池等）
