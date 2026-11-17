📁 realm-messaging-spring-boot-starter 📁 详细包结构全景

realm-messaging-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/messaging/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── MessagingAutoConfiguration.java   # [总控] 全局开关与核心抽象装配
│   ├── BinderAutoConfiguration.java      # [Binder] 类路径检测与默认 Binder 选择
│   └── MetricsAutoConfiguration.java     # [指标] 可观测性埋点
│
├── core/                                 # 传输中立的消息契约（零 Broker 依赖）
│   ├── Message.java                      # 不可变消息契约（幂等 ID、目的地、payload、header）
│   ├── MessagePublisher.java             # 发布接口
│   ├── MessageHandler.java               # 消费接口
│   └── Binder.java                       # [SPI] Broker 适配器统一接口
│
├── binder/                               # Binder 注册与选择
│   ├── BinderRegistry.java               # 收集所有 Binder 实现，按配置或类路径选择
│   └── ClasspathBinderDetector.java      # 类路径自动检测（类似 Spring Cloud Stream 的 Classpath Detection）
│
├── support/                              # 支撑工具
│   └── MessageHeaderUtils.java           # 消息头的规范化处理
│
└── metrics/                              # 可观测性
└── MessagingMetrics.java             # 发布/消费耗时、成功/失败计数（基于 Micrometer）
