📁 realm-distributed-transaction-spring-boot-starter 📁 详细包结构全景

realm-distributed-transaction-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/distributed/transaction/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── DistributedTransactionAutoConfiguration.java   # [总控] 全局开关与 Seata 客户端装配
│   ├── DataSourceProxyAutoConfiguration.java          # [代理] 多数据源场景下的 DataSourceProxy 协调
│   └── TransactionManagerAutoConfiguration.java       # [事务] 确保 JpaTransactionManager 优先定型
│
├── proxy/                                # 数据源代理协调
│   ├── SeataDataSourceProxyCoordinator.java # 为参与全局事务的 DataSource 创建 DataSourceProxy
│   └── DataSourceProxyProperties.java       # 代理策略配置（哪些数据源参与、是否跳过只读库）
│
├── transaction/                          # 事务管理器定型
│   ├── SeataTransactionManagerCustomizer.java # 在 JPA 场景下确保 JpaTransactionManager 优先
│   └── TransactionModeValidator.java          # 启动时校验事务模式与数据源代理的一致性
│
├── spi/                                  # 分布式事务抽象 SPI（为未来扩展留出空间）
│   └── DistributedTransactionSupport.java     # 标记接口：应用实现以声明采用的分布式事务方案
│
├── metrics/                              # 可观测性
│   └── DistributedTransactionMetrics.java     # 全局事务耗时、提交/回滚计数（基于 Micrometer）
│
└── properties/                           # 配置属性类
└── RealmDistributedTransactionProperties.java # 全局开关、代理策略、事务模式提示
