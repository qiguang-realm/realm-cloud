📁 realm-web-spring-boot-starter 详细包结构全景

realm-web-spring-boot-starter/
├── src/main/java/cn/realm/cloud/framework/web/
│   │
│   ├── package-info.java                 # [架构红线] 声明本模块职责边界：仅 HTTP 入站横切；
│   │                                     #   不含认证授权、不含出站客户端、不强制 OpenAPI；
│   │                                     #   标注 @ApplicationModule 依赖白名单
│   │
│   ├── autoconfigure/                    # Spring Boot 自动配置入口（独立子包，禁止被组件扫描）
│   │   ├── WebAutoConfiguration.java     # [总控] @AutoConfiguration + @ConditionalOnProperty
│   │   │                                 #   聚合所有子配置，提供 realm.web.enabled 全局开关
│   │   ├── ProblemDetailAutoConfiguration.java # [异常] 装配 GlobalExceptionHandler 与
│   │   │                                 #   ProblemDetailFactory；@ConditionalOnWebApplication(SERVLET)
│   │   ├── TraceAutoConfiguration.java   # [追踪] 装配入站 Filter、Extractor、出站 Injector、
│   │   │                                 #   TraceContextHolder；不绑定具体客户端
│   │   ├── LoggingAutoConfiguration.java # [日志] 装配 RequestLoggingFilter 与统一脱敏入口
│   │   │                                 #   SensitiveDataMasker；按载体注册 masker 实现
│   │   ├── PaginationAutoConfiguration.java # [分页] 装配 PageParamArgumentResolver 与
│   │   │                                 #   PageRequest 工厂；offset 为默认，cursor 按注解启用
│   │   ├── VersioningAutoConfiguration.java # [版本] 装配 VersionResolver 与
│   │   │                                 #   ApiVersionArgumentResolver
│   │   └── WebDefaultsAutoConfiguration.java # [基础] 装配 CorsConfigurer 与
│   │                                     #   SecurityHeadersConfigurer；默认安全而非全开
│   │
│   ├── exception/                        # 异常映射与错误契约（RFC 9457 默认路径）
│   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice；只做“异常 → ProblemDetail”
│   │   │                                 #   的转换，不写业务码判断
│   │   ├── ProblemDetailAssembler.java     # 构造 RFC 9457 响应体：type/title/status/detail/
│   │   │                                 #   instance + 扩展成员 internalCode
│   │   ├── BusinessExceptionMapper.java  # 业务异常 → HTTP 状态映射表；可被下游 SPI 覆盖
│   │   ├── ValidationExceptionMapper.java # Bean Validation / 参数校验异常 → 400 + 字段级 detail
│   │   └── ErrorProperties.java          # 错误契约选择：problem-detail（默认）/ legacy-envelope；
│   │                                     #   决定是否装配 compat 层
│   │
│   ├── trace/                            # 链路追踪（W3C Trace Context 双向闭环）
│   │   ├── TraceContextFilter.java       # 入站：从 traceparent / tracestate 提取并放入 Holder；
│   │   │                                 #   once-per-request，响应后清理
│   │   ├── TraceContextExtractor.java    # 解析 Header 为 TraceContext（traceId/spanId/flags/
│   │   │                                 #   tracestate），校验版本与格式
│   │   ├── TraceContextInjector.java     # 出站：将当前 TraceContext 注入 carrier（HTTP Header /
│   │   │                                 #   消息属性 / 异步上下文）；传输中立，不绑定客户端
│   │   ├── TraceContextHolder.java       # 线程 / Reactor 上下文存取抽象；提供 get/ set/ clear/
│   │   │                                 #   wrap(Runnable/Callable)；供 Feign、MQ、异步执行器复用
│   │   └── TraceProperties.java          # 排除路径、采样策略开关、tracestate 透传开关
│   │
│   ├── logging/                          # 请求日志与敏感数据脱敏（多载体覆盖）
│   │   ├── RequestLoggingFilter.java     # 记录 method/URI/耗时/状态码；URI 必须经脱敏后再记录
│   │   ├── SensitiveDataMasker.java      # 统一脱敏入口，按载体分发到 masker/ 下实现；
│   │   │                                 #   下游只依赖此入口，不直接调具体 masker
│   │   ├── LoggingProperties.java        # 脱敏字段规则、Payload 大小上限、是否记录 Header
│   │   └── masker/                       # 按载体拆分的脱敏实现（结构可见的覆盖契约）
│   │       ├── BodyFieldMasker.java      # JSON / 表单 Body 敏感字段掩码
│   │       ├── QueryParamMasker.java     # Query String 敏感参数剥离 / 掩码
│   │       └── HeaderMasker.java         # Authorization、租户密钥等敏感头掩码
│   │
│   ├── pagination/                       # 分页绑定（offset 与 cursor 并存，不锁死模型）
│   │   ├── PageRequest.java              # 分页请求抽象；下游依赖抽象而非具体实现
│   │   ├── OffsetPageRequest.java        # pageNo / pageSize；适合管理后台与随机跳页
│   │   ├── CursorPageRequest.java        # cursor / limit / direction；适合面向用户的 list API
│   │   ├── PageParamArgumentResolver.java # 默认解析 pageNo/pageSize 为 OffsetPageRequest；
│   │   │                                 #   返回类型为 PageRequest 抽象
│   │   └── PaginationProperties.java     # maxPageSize、defaultLimit、cursor 编码方式
│   │
│   ├── versioning/                       # API 版本解析
│   │   ├── VersionResolver.java          # 版本提取接口（Header / Path / MediaType 可插拔）
│   │   └── ApiVersionArgumentResolver.java # 让 Controller 直接接收版本参数，解耦解析与使用
│   │
│   ├── web/                              # Web 默认配置（默认安全）
│   │   ├── CorsConfigurer.java           # 默认拒绝跨域，白名单显式配置；禁止 * + credentials
│   │   └── SecurityHeadersConfigurer.java # X-Content-Type-Options / X-Frame-Options /
│   │                                     #   Referrer-Policy / HSTS 等安全响应头
│   │
│   ├── compat/                           # 遗留兼容层（与推荐路径物理隔离）
│   │   └── ApiResponseBodyAdvice.java    # 旧版统一包装器；仅 ErrorProperties=legacy-envelope
│   │                                     #   时装配，不与 ProblemDetail 并列推荐
│   │
│   └── properties/
│       └── WebProperties.java            # 仅保留跨子域全局开关；领域配置归入各领域包
│
└── src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
# [必须] Boot 3 自动配置注册文件；列出全部
#   *AutoConfiguration 全限定名；替代已废弃的
#   spring.factories
