📁 realm-payment 📁 详细包结构全景

realm-payment/
└── src/main/java/cn/realm/cloud/payment/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── events/                               # 对外发布的领域事件
│   ├── package-info.java                 # @NamedInterface("events")
│   ├── PaymentSucceededEvent.java        # 支付成功事件（供订单、通知、analytics 消费）
│   ├── PaymentFailedEvent.java           # 支付失败事件
│   └── RefundCompletedEvent.java         # 退款完成事件
│
└── internal/                             # 私有实现区（Modulith 强制隔离）
│
├── config/                           # 模块级配置
│   ├── PaymentConfig.java            # 事务管理器、通用 Bean
│   └── ChannelConfig.java            # 三方支付渠道 SDK 的初始化配置
│
├── payment/                          # === 子域 1：支付单管理 (Payment Order) ===
│   ├── domain/
│   │   ├── model/
│   │   │   ├── PaymentOrder.java     # [聚合根] 支付单（含金额、状态机流转、支付渠道标识）
│   │   │   ├── PaymentStatus.java    # 枚举：PENDING, SUCCESS, FAILED, CLOSED, REFUNDED
│   │   │   └── PaymentChannel.java   # 枚举：WECHAT, ALIPAY, BALANCE
│   │   └── repository/
│   │       └── PaymentOrderRepository.java # [端口]
│   ├── application/
│   │   ├── PaymentCommandService.java # 创建支付单、发起支付（调用网关）
│   │   ├── PaymentQueryService.java   # 查询支付状态
│   │   └── PaymentStateMachine.java   # 严格的状态流转控制（如：只有 PENDING 才能变 SUCCESS）
│   ├── infrastructure/
│   │   └── persistence/              # DO, Mapper (pay_order), Adapter
│   └── api/
│       ├── PaymentAppController.java # 客户端发起支付、查询结果
│       └── dto/
│
├── callback/                         # === 子域 2：三方回调 (Callback / Webhook) ===
│   ├── application/
│   │   ├── PaymentCallbackService.java # 处理微信/支付宝的回调通知（验签、幂等、更新状态）
│   │   └── CallbackIdempotentGuard.java# 回调幂等守卫（基于 Redis 或 DB 唯一索引）
│   └── api/
│       ├── WechatCallbackController.java # /callback/wechat
│       └── AlipayCallbackController.java # /callback/alipay
│
├── refund/                           # === 子域 3：退款管理 (Refund) ===
│   ├── domain/
│   │   ├── model/
│   │   │   ├── RefundOrder.java      # [聚合根] 退款单
│   │   │   └── RefundStatus.java
│   │   └── repository/
│   │       └── RefundOrderRepository.java
│   ├── application/
│   │   └── RefundCommandService.java # 发起退款、处理退款回调
│   ├── infrastructure/
│   │   └── persistence/              # 映射 pay_refund
│   └── api/
│       └── RefundAdminController.java # 管理后台发起退款
│
├── transaction/                      # === 子域 4：交易流水与对账 (Transaction & Reconciliation) ===
│   ├── domain/
│   │   └── model/
│   │       └── TransactionRecord.java # [聚合根] 不可变的资金流水账目
│   ├── application/
│   │   ├── TransactionRecordService.java # 记录资金变动流水
│   │   └── ReconciliationService.java    # 每日定时对账（与微信/支付宝账单比对）
│   ├── infrastructure/
│   │   └── persistence/              # 映射 pay_transaction, pay_reconciliation_diff
│   └── api/
│       └── TransactionAdminController.java
│
└── gateway/                          # === 防腐层 (Anti-Corruption Layer) ===
├── PaymentGateway.java           # [核心端口] 统一支付网关接口（pay, refund, query）
├── wechat/
│   └── WechatPaymentGateway.java # 实现 PaymentGateway，封装微信 SDK，屏蔽类型
├── alipay/
│   └── AlipayPaymentGateway.java # 实现 PaymentGateway，封装支付宝 SDK，屏蔽类型
└── mock/
└── MockPaymentGateway.java   # 测试或本地开发用的假支付网关
