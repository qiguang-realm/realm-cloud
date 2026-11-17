📁 realm-authorization-spring-boot-starter 📁 详细包结构全景

realm-authorization-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/authorization/
├── package-info.java
├── autoconfigure/                    # 仅装配，不实现
│   ├── AuthorizationAutoConfiguration.java
│   ├── MethodSecurityAutoConfiguration.java
│   └── WebSecurityAutoConfiguration.java
├── domain/                           # [新增] 授权领域模型（零框架依赖）
│   ├── AuthorizationQuery.java
│   ├── RealmAuthorizationDecision.java
│   └── relation/                     # [预留] 关系元组模型
│       ├── RelationTuple.java
│       └── TupleReader.java
├── core/                             # 决策核心（对接 Spring Security AuthorizationManager）
│   ├── RealmAuthorizationManager.java
│   └── PermissionEvaluator.java      # [SPI] 传统权限评估
├── method/                           # 方法级安全集成
│   ├── MethodSecurityConfiguration.java  # 定义 Advisors 等 Bean
│   └── RealmPreAuthorizeAuthorizationManager.java
├── web/                              # URL 级安全集成
│   ├── UrlAuthorizationManager.java
│   └── AuthorizationFailureHandler.java
├── event/                            # [新增] 授权决策事件
│   └── AuthorizationDecisionEvent.java
└── properties/
└── RealmAuthorizationProperties.java


realm-authorization-spring-boot-starter/
└── src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
# [必须] Spring Boot 3 自动配置注册文件
