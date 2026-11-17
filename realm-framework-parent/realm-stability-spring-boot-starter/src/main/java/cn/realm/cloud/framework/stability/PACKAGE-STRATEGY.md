📁 realm-stability-spring-boot-starter 📁 详细包结构全景

realm-stability-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/stability/
│
├── package-info.java                     # [必须] 模块定位与架构红线
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口（独立子包）[reference:1]
│   ├── StabilityAutoConfiguration.java   # [总控] 聚合所有子配置，全局开关
│   ├── ObservabilityAutoConfiguration.java # [可观测性] 指标、追踪、日志
│   ├── ResilienceAutoConfiguration.java  # [保护] 限流、熔断、重试、隔离
│   └── IdempotencyAutoConfiguration.java # [幂等] 幂等键处理
│
├── observability/                        # 可观测性（感知端）
│   ├── metrics/
│   │   ├── StabilityMetrics.java         # 统一指标埋点（基于 Micrometer）
│   │   └── CustomMeterBinder.java        # 自定义指标绑定器
│   ├── tracing/
│   │   ├── TraceContextPropagator.java   # W3C Trace Context 传播
│   │   └── TracingConfiguration.java     # OpenTelemetry/Micrometer Tracing 配置
│   └── logging/
│       └── StructuredLoggingSupport.java # 结构化日志辅助
│
├── protection/                           # 稳定性保护（执行端）[reference:3]
│   ├── ratelimit/
│   │   ├── RateLimitAspect.java          # 限流切面
│   │   └── RateLimiterRegistry.java      # 限流器注册表
│   ├── circuitbreaker/
│   │   ├── CircuitBreakerAspect.java     # 熔断切面
│   │   └── CircuitBreakerRegistry.java   # 熔断器注册表
│   ├── retry/
│   │   └── RetryAspect.java              # 重试切面
│   └── bulkhead/
│       └── BulkheadAspect.java           # 舱壁隔离
│
├── idempotency/                          # 幂等性支撑
│   ├── Idempotent.java                   # 幂等注解
│   ├── IdempotentAspect.java             # 幂等切面
│   └── IdempotentKeyGenerator.java       # 幂等键生成器
│
├── integration/                          # 感知与执行的闭环
│   └── MetricsDrivenResilience.java      # 基于指标动态调整保护阈值
│
└── properties/                           # 配置属性类
├── StabilityProperties.java          # 全局开关
├── ObservabilityProperties.java      # 可观测性配置
└── ResilienceProperties.java         # 保护策略配置
