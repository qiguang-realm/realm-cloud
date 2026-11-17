📁 realm-data-redis-spring-boot-starter 📁 详细包结构全景

realm-data-redis-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/data/redis/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── RedisDataAutoConfiguration.java   # [总控] 全局开关与核心抽象装配
│   ├── RedisSerializerAutoConfiguration.java # [序列化] JavaTimeModule 与通用序列化器
│   ├── RedisCacheAutoConfiguration.java  # [缓存] RedisCacheManager 默认配置
│   └── RedissonConnectionAutoConfiguration.java # [连接] Redisson 作为唯一连接工厂
│
├── serializer/                           # 序列化策略（核心价值）
│   ├── RealmRedisSerializer.java         # [默认] GenericJackson2JsonRedisSerializer 的 realm 定制
│   └── SerializerFactory.java            # 序列化器的条件化工厂（key=String, value=JSON）
│
├── cache/                                # 缓存抽象配置
│   ├── RealmRedisCacheConfiguration.java # 默认 TTL、key 前缀、null 值策略
│   └── CacheTtlFunction.java             # 按缓存名称动态 TTL（可选）
│
├── connection/                           # 连接工厂治理
│   └── RedissonConnectionFactoryCustomizer.java # 确保 Redisson 是唯一 RedisConnectionFactory
│
├── metrics/                              # 可观测性
│   └── RedisDataMetrics.java             # 命令耗时、连接池状态（基于 Micrometer）
│
└── properties/                           # 配置属性类
├── RealmRedisProperties.java         # 全局开关、序列化选项
└── RealmRedisCacheProperties.java    # 缓存 TTL、前缀、null 值策略
