📁 realm-feature-env-spring-boot-starter 📁 详细包结构全景

realm-feature-env-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/featureenv/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── FeatureEnvAutoConfiguration.java        # [总控] 全局开关与核心上下文装配
│   ├── FeatureEnvWebAutoConfiguration.java     # [Web] Servlet / Reactive 入站染色过滤器
│   └── FeatureEnvLoadBalancerAutoConfiguration.java # [LB] Hint 机制与实例筛选集成
│
├── context/                              # 特性环境上下文（零外部依赖）
│   ├── FeatureEnvContext.java            # [核心接口] 获取当前流量标签
│   └── FeatureEnvContextHolder.java      # [实现] 基于 ThreadLocal 的上下文持有者
│
├── filter/                               # 入站流量染色（可选栈适配）
│   ├── FeatureEnvServletFilter.java      # Servlet 栈：OncePerRequestFilter
│   └── FeatureEnvWebFilter.java          # Reactive 栈：WebFilter
│
├── loadbalancer/                         # 负载均衡标签路由集成
│   ├── RealmHintSupplier.java            # 基于 FeatureEnvContext 的 Hint 提供者
│   └── FeatureEnvInstanceFilter.java     # 实例元数据与标签的匹配逻辑
│
├── propagation/                          # 出站传播 SPI（供 realm-openfeign 等消费）
│   └── FeatureEnvPropagator.java         # 将当前标签注入出站请求头 / 上下文
│
└── properties/                           # 配置属性类
└── FeatureEnvProperties.java         # 头名称、基线回落策略、排除路径等
