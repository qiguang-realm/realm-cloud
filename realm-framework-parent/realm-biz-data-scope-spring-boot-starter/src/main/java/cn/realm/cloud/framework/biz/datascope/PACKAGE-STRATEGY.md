📁 realm-biz-data-scope-spring-boot-starter 📁 详细包结构全景

realm-biz-data-scope-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/biz/datascope/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── DataScope.java                        # [公共 API] 数据范围枚举（ALL / DEPT / SELF / CUSTOM / DENY）
├── DataScopeRule.java                    # [公共 API] 单条资源规则（资源标识 + 范围 + 自定义值）
├── DataScopeContext.java                 # [公共 API] 当前调用方的规则集合
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── DataScopeAutoConfiguration.java   # [总控] 全局开关与核心 Bean 装配
│   └── DataScopeMybatisAutoConfiguration.java # [拦截器] 将 DataScopeInterceptor 注入 MybatisPlusInterceptor 链
│
├── interceptor/                          # 拦截器与处理器（SQL 织入）
│   ├── DataScopeInterceptor.java         # [核心] 实现 MyBatis-Plus 的 DataPermissionInterceptor
│   └── DataScopeHandler.java             # [核心] 实现 DataPermissionHandler，解析注解并生成 SQL 谓词
│
├── context/                              # 上下文管理（线程级传播）
│   ├── DataScopeContextHolder.java       # [核心] 基于 TransmittableThreadLocal 的上下文持有者
│   └── DataScopeContextResolver.java     # [SPI] 从应用安全层解析当前用户规则集合
│
├── sql/                                  # SQL 谓词生成策略
│   ├── DataScopeSqlPredicateProvider.java # [SPI] 将 DataScopeRule 翻译为 SQL WHERE 片段
│   └── DefaultSqlPredicateProvider.java  # [默认] 处理 ALL / DENY / CUSTOM / SELF 的标准谓词
│
└── properties/                           # 配置属性类
└── DataScopeProperties.java          # 全局开关、fail-close 策略、资源-表名映射


realm-biz-data-scope-spring-boot-starter/
└── src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
# [必须] Spring Boot 3 自动配置注册文件
