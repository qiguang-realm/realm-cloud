📁 realm-config-client-spring-boot-starter 📁 详细包结构全景

realm-config-client-spring-boot-starter/
└── src/main/java/cn/realm/cloud/framework/config/client/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── autoconfigure/                        # Spring Boot 3 自动配置入口
│   ├── RealmConfigClientAutoConfiguration.java # [总控] 全局开关与核心抽象装配
│   └── ConfigDataAutoConfiguration.java  # [ConfigData] 委托型 Resolver/Loader 的装配
│
├── configdata/                           # ConfigData API 适配（核心）
│   ├── RealmConfigDataLocationResolver.java # 委托型 Resolver，识别配置中心前缀
│   ├── RealmConfigDataLoader.java        # 委托型 Loader，调用具体客户端拉取配置
│   └── ConfigClientAdapter.java          # [SPI] 具体配置中心客户端的统一适配接口
│
├── refresh/                              # 动态刷新与变更事件
│   ├── RealmConfigChangeEvent.java       # 配置变更事件（供 realm-stability 等消费）
│   └── ConfigRefreshListener.java        # 监听 ContextRefresher 的刷新动作
│
├── security/                             # 敏感配置解密
│   └── SensitiveConfigDecryptor.java     # [SPI] 配置值解密钩子（如 ENC(...) 格式）
│
├── metrics/                              # 可观测性
│   └── ConfigClientMetrics.java          # 配置拉取耗时、刷新次数、失败计数
│
└── properties/                           # 配置属性类
└── RealmConfigClientProperties.java  # 全局开关、适配器选择、解密开关



realm-config-client-spring-boot-starter/
├── src/main/resources/META-INF/spring/
│   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       # [必须] Spring Boot 3 自动配置注册文件
│
└── src/main/resources/META-INF/
└── spring.factories                    # [必须] 仅注册 ConfigDataLocationResolver
# 注意：ConfigDataLocationResolver 不属于 AutoConfiguration，
# 必须通过 spring.factories 的 org.springframework.boot.context.config.ConfigDataLocationResolver
# 键来注册，否则 resolver 不会被 ConfigDataEnvironmentPostProcessor 发现。
