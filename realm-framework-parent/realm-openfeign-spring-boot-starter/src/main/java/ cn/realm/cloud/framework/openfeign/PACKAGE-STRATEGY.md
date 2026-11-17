📁 realm-openfeign-spring-boot-starter 📁 详细包结构全景

realm-openfeign-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/openfeign/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口（独立子包）
│   ├── RealmOpenFeignAutoConfiguration.java # [总控] 全局开关，聚合各子配置
│   ├── RetryAutoConfiguration.java       # [重试] 幂等性感知的重试策略装配
│   ├── PropagationAutoConfiguration.java  # [传播] 上下文传播拦截器链的条件装配
│   └── MetricsAutoConfiguration.java     # [指标] 可观测性埋点
│
├── interceptor/                          # RequestInterceptor 链（出站请求增强）
│   ├── TenantFeignInterceptor.java       # 传播租户上下文（TenantContext）
│   ├── TraceFeignInterceptor.java        # 传播 W3C Trace Context（traceparent）
│   ├── AuthFeignInterceptor.java         # 传播认证令牌（Authorization）
│   └── SensitiveUrlGuardInterceptor.java # 检测敏感参数泄漏到 URL 并告警[reference:3]
│
├── retry/                                # 重试治理（幂等性感知）
│   ├── IdempotentRetryer.java            # 基于 HTTP 方法幂等性的重试决策器
│   └── RetryProperties.java              # 重试配置（退避策略、可重试异常白名单）
│
├── decoder/                              # 错误解码与脱敏
│   └── MaskingErrorDecoder.java          # 包装默认 ErrorDecoder，剥离响应体敏感信息[reference:4]
│
├── logger/                               # 日志脱敏
│   └── MaskingFeignLogger.java           # 包装 Feign Logger，对请求/响应体进行字段级掩码[reference:5]
│
├── metrics/                              # 可观测性
│   └── FeignClientMetrics.java           # 调用耗时、成功率、重试次数（基于 Micrometer）
│
└── properties/                           # 配置属性类
├── OpenFeignProperties.java          # 全局开关（realm.openfeign.enabled）
└── PropagationProperties.java        # 各上下文传播的启用开关
