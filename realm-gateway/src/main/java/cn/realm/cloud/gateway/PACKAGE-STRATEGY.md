📁 realm-gateway 📁 详细包结构全景

realm-gateway/
└── src/main/java/cn/realm/cloud/gateway/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── GatewayApplication.java               # [必须] @SpringBootApplication 启动类
│
├── autoconfigure/                        # 网关自身的自动配置（非 starter 分发）
│   ├── GatewayRouteAutoConfiguration.java  # 路由装配（Nacos 动态路由 / YAML 静态路由）
│   ├── GatewaySecurityAutoConfiguration.java # WebFlux 安全配置（ServerHttpSecurity）
│   └── GatewayFilterAutoConfiguration.java  # GlobalFilter 链的条件装配
│
├── filter/                               # GlobalFilter（按关注点单一职责拆分）
│   ├── order/
│   │   └── GatewayFilterOrder.java       # 过滤器顺序常量（禁止硬编码魔数）
│   ├── auth/
│   │   ├── JwtAuthGlobalFilter.java      # Token 合法性校验 + 白名单放行
│   │   └── TrustedHeaderGlobalFilter.java # 向下游注入可信头（X-User-Id / X-Tenant-Id）
│   ├── trace/
│   │   └── TraceContextGlobalFilter.java # W3C traceparent 提取与回写
│   ├── logging/
│   │   └── AccessLogGlobalFilter.java    # 记录路径、方法、耗时、状态码（不修改 body）
│   └── ratelimit/
│       └── RateLimitGlobalFilter.java    # 基于 Redis 的令牌桶限流（ReactiveRedisTemplate）
│
├── route/                                # 路由管理
│   ├── DynamicRouteService.java          # 从 Nacos 配置中心监听路由变更，刷新 RouteDefinition
│   └── RouteDefinitionConverter.java     # 将外部路由配置（JSON/YAML）转换为 Spring 的 RouteDefinition
│
├── exception/                            # WebFlux 错误处理（不是 @RestControllerAdvice）
│   ├── GlobalErrorWebExceptionHandler.java # 实现 ErrorWebExceptionHandler
│   └── ProblemDetailFactory.java         # 构造 RFC 9457 ProblemDetail（WebFlux 适配）
│
├── security/                             # WebFlux 安全配置（仅做网关层校验）
│   └── GatewaySecurityConfig.java        # ServerHttpSecurity 配置，禁止引入 SecurityFilterChain
│
├── support/                              # 支撑工具
│   ├── ResponseWriter.java               # 非阻塞错误响应写入（Mono<Void>）
│   └── ReactiveJwtDecoder.java           # 响应式 JWT 解码（不阻塞事件循环）
│
└── properties/                           # 配置属性类
├── GatewayAuthProperties.java        # 白名单路径、JWT 公钥/密钥
├── GatewayTraceProperties.java       # 追踪头名称、排除路径
└── GatewayRateLimitProperties.java   # 限流规则（按路由/IP/用户维度）
