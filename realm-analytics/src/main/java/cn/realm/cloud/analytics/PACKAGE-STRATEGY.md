📁 realm-analytics 📁 详细包结构全景

realm-analytics/
└── src/main/java/cn/realm/cloud/analytics/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── AnalyticsAutoConfiguration.java   # [总控] 全局开关、查询服务装配
│   └── ProjectionAutoConfiguration.java  # [投影] 事件监听器与投影任务的条件装配
│
├── internal/                             # 私有实现（Spring Modulith 隔离）
│   │
│   ├── projection/                       # 事件消费与读模型构建（CQRS 写端）
│   │   ├── user/
│   │   │   └── UserAnalyticsProjector.java      # 监听 realm-workforce-identity 事件，更新用户统计读模型
│   │   ├── customer/
│   │   │   └── CustomerAnalyticsProjector.java  # 监听 realm-customer 事件，更新会员注册、活跃读模型
│   │   └── payment/
│   │       └── PaymentAnalyticsProjector.java   # 监听 realm-payment 事件，更新交易额、成功率读模型
│   │
│   ├── query/                            # 查询服务编排（CQRS 读端）
│   │   ├── DashboardQueryService.java    # 仪表盘数据聚合查询（跨读模型关联）
│   │   ├── ReportQueryService.java       # 复杂报表查询
│   │   └── StatisticsQueryService.java   # 通用时间维度统计查询
│   │
│   ├── domain/                           # 读模型实体（只读，无充血模型行为）
│   │   ├── UserStatistics.java
│   │   ├── PaymentStatistics.java
│   │   └── DailyActiveStatistics.java
│   │
│   ├── infrastructure/                   # 读模型持久化
│   │   ├── persistence/
│   │   │   ├── dataobject/               # 数据库映射对象（analytics_* 表）
│   │   │   │   ├── UserStatisticsDO.java
│   │   │   │   └── PaymentStatisticsDO.java
│   │   │   ├── mapper/
│   │   │   │   └── AnalyticsMapper.java  # 使用 MyBatis-Plus 直接写复杂 SQL/动态 SQL 进行聚合
│   │   │   └── query/
│   │   │       └── AnalyticsQueryRepository.java # 封装查询读模型的入口
│   │   └── client/                       # 只读副本路由（可选）
│   │       └── ReplicaRoutingConfig.java # 如果直接查只读库，配置 @DS("replica")
│   │
│   └── api/                              # 入站适配器
│       ├── DashboardController.java      # GET /admin/analytics/dashboard 仪表盘数据
│       ├── ReportController.java         # GET /admin/analytics/report 导出报表
│       ├── dto/                          # HTTP 契约（不跨模块复用）
│       │   ├── DashboardSummaryResponse.java
│       │   └── TimeSeriesDataRequest.java
│       └── converter/
│           └── AnalyticsConverter.java   # DO / 读模型 与 DTO 的转换
│
└── (注意：没有 facade 接口，因为 analytics 是叶子节点，只消费，不被依赖)
