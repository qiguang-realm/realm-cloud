📁 realm-data-access-spring-boot-starter 📁 详细包结构全景

realm-data-access-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/data/access/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── DataAccessAutoConfiguration.java  # [总控] 全局开关与核心基础设施装配
│   ├── MultiDataSourceAutoConfiguration.java # [多源] 动态数据源路由的默认装配
│   └── TransactionAutoConfiguration.java # [事务] 默认事务管理器（可被 ORM 模块覆盖）
│
├── routing/                              # 多数据源路由核心
│   ├── DynamicRoutingDataSource.java     # 动态路由数据源（基于 ThreadLocal 的路由键）
│   └── DataSourceContextHolder.java      # 线程级数据源标识持有者（支持嵌套与清理）
│
├── transaction/                          # 事务管理基础设施
│   ├── DataSourceTransactionManagerFactory.java # 事务管理器工厂（供路由数据源使用）
│   └── TransactionManagerCustomizer.java # 允许 ORM 模块（如 JPA）定制或覆盖事务管理器的 SPI
│
├── support/                              # 支撑组件
│   ├── DataSourceHealthIndicator.java    # 多数据源健康检查（集成 Actuator）
│   └── ConnectionPoolMetricsBinder.java  # HikariCP 连接池指标绑定（基于 Micrometer）
│
└── properties/                           # 配置属性类
├── RealmDataAccessProperties.java    # 全局开关、默认路由键、健康检查配置
└── MultiDataSourceProperties.java    # 多数据源列表与各源连接池参数
