📁 realm-customer 📁 详细包结构全景

realm-customer/
└── src/main/java/cn/realm/cloud/customer/
│
├── package-info.java                     # [必须] 声明模块边界与架构红线
│
├── CustomerFacade.java                   # [公共 API] 跨模块同步调用门面（如有需要）
├── CustomerDto.java                      # [公共 API] 跨模块只读契约（如供 analytics 查询基础信息）
│
├── events/                               # [公共 API] 对外发布的领域事件
│   ├── package-info.java                 # @NamedInterface("events")
│   ├── CustomerRegisteredEvent.java      # 客户注册成功事件（供 analytics、营销模块消费）
│   ├── CustomerTierChangedEvent.java     # 客户等级变更事件
│   └── CustomerStatusChangedEvent.java   # 客户状态变更事件
│
└── internal/                             # 私有实现区（Modulith 强制隔离）
│
├── config/                           # 模块内部配置
│   ├── CustomerSecurityConfig.java   # C端密码哈希、令牌生成策略（如需要）
│   └── CaptchaConfig.java            # 验证码防刷策略
│
├── profile/                          # === 子域 1：客户档案 (Customer Profile) ===
│   ├── domain/
│   │   ├── model/
│   │   │   └── Customer.java         # [聚合根] 客户实体（包含修改资料、锁定、注销等行为）
│   │   └── repository/
│   │       └── CustomerRepository.java # [端口] 客户持久化接口
│   ├── application/
│   │   ├── CustomerCommandService.java # 注册、修改资料、手机号换绑
│   │   └── CustomerQueryService.java   # 个人中心查询
│   ├── infrastructure/
│   │   ├── persistence/
│   │   │   ├── CustomerDO.java       # 映射 member_user 表
│   │   │   ├── CustomerMapper.java   # MyBatis-Plus Mapper
│   │   │   ├── MybatisCustomerRepository.java # 实现 CustomerRepository
│   │   │   └── CustomerPersistenceConverter.java
│   │   └── security/
│   │       └── CustomerPasswordEncoder.java # 密码加密实现
│   └── api/
│       ├── CustomerAppController.java      # 路径: /app/customer/profile
│       ├── dto/
│       │   ├── CustomerRegisterRequest.java
│       │   ├── CustomerProfileUpdateRequest.java
│       │   └── CustomerProfileResponse.java
│       └── converter/
│           └── CustomerAppConverter.java
│
├── loyalty/                          # === 子域 2：忠诚度 (Loyalty & Rewards) ===
│   ├── domain/
│   │   ├── model/
│   │   │   ├── MemberTier.java       # [聚合根] 会员等级（升降级规则）
│   │   │   ├── PointTransaction.java # [聚合根] 积分/经验流水
│   │   │   ├── CheckInRule.java      # [聚合根] 签到规则
│   │   │   └── CheckInRecord.java    # [聚合根] 签到记录
│   │   ├── repository/
│   │   │   ├── TierRepository.java
│   │   │   └── PointRepository.java
│   │   └── service/
│   │       └── TierEvaluationDomainService.java # 根据经验计算等级跃迁
│   ├── application/
│   │   ├── PointCommandService.java  # 积分/经验发放、扣减（含幂等处理）
│   │   ├── CheckInCommandService.java # 执行签到、计算连续天数
│   │   └── LoyaltyQueryService.java  # 查询积分余额、签到日历
│   ├── infrastructure/
│   │   └── persistence/              # DO, Mapper, Adapter (member_tier, member_point_transaction等)
│   └── api/
│       ├── LoyaltyAppController.java # 路径: /app/customer/loyalty
│       └── dto/                      # 签到请求、积分流水响应
│
├── address/                          # === 子域 3：收货地址 (Address Book) ===
│   ├── domain/
│   │   └── model/
│   │       └── DeliveryAddress.java  # [聚合根] 地址实体（处理默认地址互斥逻辑）
│   ├── application/
│   │   └── AddressApplicationService.java
│   ├── infrastructure/
│   │   └── persistence/              # 映射 member_address 表
│   └── api/
│       └── AddressAppController.java # 路径: /app/customer/address
│
└── tag/                              # === 子域 4：客户标签 (Tags) ===
├── domain/
│   ├── model/
│   │   └── CustomerTag.java
│   └── repository/
│       └── TagRepository.java
├── application/
│   └── TagApplicationService.java
├── infrastructure/
│   └── persistence/              # 映射 member_tag, member_user_tag
└── api/
└── TagAdminController.java   # 路径: /admin/customer/tag (管理端打标)
