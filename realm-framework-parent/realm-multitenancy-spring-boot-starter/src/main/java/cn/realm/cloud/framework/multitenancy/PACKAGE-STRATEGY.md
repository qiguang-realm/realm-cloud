📁 realm-multitenancy-spring-boot-starter 📁 详细包结构全景

realm-multitenancy-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/multitenancy/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── MultitenancyAutoConfiguration.java    # [总控] 全局开关与核心抽象装配
│   ├── WebMultitenancyAutoConfiguration.java # [Web] Servlet 环境下的租户解析过滤器
│   └── AsyncMultitenancyAutoConfiguration.java # [异步] 上下文跨线程传播增强
│
├── context/                              # 租户上下文核心（零外部依赖）
│   ├── TenantContext.java                # [核心接口] 获取当前租户标识
│   ├── TenantContextHolder.java          # [实现] 基于 ThreadLocal / TransmittableThreadLocal
│   └── TenantContextProperties.java      # 上下文传播模式配置
│
├── resolver/                             # 租户解析 SPI 与内置实现
│   ├── TenantResolver.java               # [SPI] 解析接口：String resolve(T request)
│   ├── HeaderTenantResolver.java         # 从 X-Tenant-Id 请求头解析
│   └── CompositeTenantResolver.java      # 按顺序组合多个 Resolver，第一个命中生效
│
├── lifecycle/                            # 请求边界生命周期
│   ├── TenantContextFilter.java          # OncePerRequestFilter：入站解析 → 设置上下文 → finally 清理
│   └── TenantContextCleaner.java         # 非 Web 场景的手动清理入口
│
└── spi/                                  # 隔离模型注入点（只定义契约，不实现）
└── TenantIsolationStrategy.java      # [标记接口] 应用实现此接口来表达隔离模型
