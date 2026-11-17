📁 realm-mybatis-plus-spring-boot-starter 📁 详细包结构全景

realm-mybatis-plus-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/mybatisplus/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── RealmMybatisPlusAutoConfiguration.java
│   └── RealmMybatisPlusInterceptorAutoConfiguration.java
│
├── interceptor/                          # 拦截器 SPI 与内置拦截器
│   ├── RealmInterceptorOrder.java        # 统一的拦截器顺序常量
│   ├── InnerInterceptorSupplier.java     # [SPI] 供下游模块贡献拦截器
│   ├── PaginationInnerInterceptorSupplier.java
│   ├── OptimisticLockerInnerInterceptorSupplier.java
│   └── BlockAttackInnerInterceptorSupplier.java
│
├── handler/                              # 审计字段自动填充
│   ├── RealmMetaObjectHandler.java
│   └── AuditingProperties.java
│
├── support/                              # 可选扩展
│   └── RealmIdentifierGenerator.java     # ID 生成器桥接（可选）
│
├── metrics/                              # 可观测性（可选）
│   └── MybatisPlusMetrics.java
│
└── properties/                           # 配置属性类
└── RealmMybatisPlusProperties.java
