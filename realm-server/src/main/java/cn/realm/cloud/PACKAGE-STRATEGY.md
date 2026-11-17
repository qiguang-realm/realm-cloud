📁 realm-server 📁 详细包结构全景

realm-server/
├── pom.xml                                     # 聚合所有业务模块与基础 starter，配置 spring-boot-maven-plugin
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── cn/
│   │   │       └── realm/
│   │   │           └── cloud/
│   │   │               ├── ServerApplication.java  <-- [核心] 启动类（必须直接放在 cloud 包下）
│   │   │               ├── package-info.java       <-- [必须] 描述根包定位与 Modulith 规则
│   │   │               │
│   │   │               └── server/                 <-- [可选] 全局配置包（通常为空）
│   │   │                   └── config/
│   │   │                       └── GlobalServerConfig.java # 极少数需要强绑定的跨模块全局配置
│   │   │
│   │   └── resources/
│   │       ├── application.yml                 # 主配置文件（全局配置、模块开关、Nacos 接入）
│   │       ├── application-dev.yml             # 开发环境配置
│   │       ├── application-prod.yml            # 生产环境配置
│   │       ├── logback-spring.xml              # 日志配置（审计日志路由、TraceId 染色）
│   │       └── META-INF/
│   │           └── spring.factories            # 仅在需要额外扫描外部包时存在（通常不需要）
│   │
│   └── test/
│       └── java/
│           └── cn/
│               └── realm/
│                   └── cloud/
│                       └── ModularityTest.java # [必须] 模块边界验证门禁
