📁 realm-websocket-spring-boot-starter 📁 详细包结构全景

realm-websocket-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/websocket/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口（独立子包）
│   ├── WebSocketAutoConfiguration.java   # [总控] 条件装配 WebSocket 基础设施
│   ├── RelayAutoConfiguration.java       # [中继] 按需装配 MessageBus 实现
│   └── MetricsAutoConfiguration.java     # [指标] 连接与消息速率指标
│
├── core/                                 # 核心契约与领域模型（六边形内部）
│   ├── SessionRegistry.java              # 本地连接注册表（ConcurrentHashMap）
│   ├── RelayEnvelope.java                # 中继信封：target / room / payload
│   └── MessageBus.java                   # 中继 SPI：publish / subscribe
│
├── relay/                                # 中继实现（出站适配器）
│   ├── redis/
│   │   └── RedisMessageBus.java          # 基于 Redis Pub/Sub 的跨节点广播
│   └── inmemory/
│       └── InMemoryMessageBus.java       # 单机退化实现（@ConditionalOnMissingBean）
│
├── handler/                              # 入站适配器（WebSocket 连接处理）
│   ├── RealmWebSocketHandler.java        # 文本消息处理器：注册/注销、消息路由
│   └── HandshakeInterceptor.java         # 握手阶段的主体绑定与鉴权钩子
│
└── metrics/                              # 可观测性
└── WebSocketMetrics.java             # 连接数、消息速率、中继收发计数
