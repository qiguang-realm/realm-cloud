📁 realm-devtools 📁 详细包结构全景

realm-devtools/
└── src/main/java/cn/realm/cloud/devtools/
│
├── package-info.java                     # [必须] 模块定位与架构红线声明
│
├── internal/                             # 私有实现区（模块内聚）
│   │
│   ├── generator/                        # === 能力 1：代码生成器（开发期工具） ===
│   │   ├── MyBatisPlusGenerator.java     # [核心] 包含 main() 方法的独立生成器
│   │   ├── constant/                     # 模板常量、默认包名映射
│   │   ├── converter/                    # 表结构元数据(TableMeta) 与 生成器配置的转换
│   │   └── template/                     # 自定义的 Freemarker 模板（可选）
│   │
│   ├── datasource/                       # === 能力 2：数据源管理（元数据 CRUD） ===
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   └── DataSourceConfig.java # 数据源配置领域模型（包含加密逻辑）
│   │   │   └── repository/
│   │   │       └── DataSourceRepository.java
│   │   ├── application/
│   │   │   ├── DataSourceCommandService.java # 保存/修改/删除数据源
│   │   │   └── DataSourceQueryService.java   # 查询数据源列表、测试连接
│   │   ├── infrastructure/
│   │   │   └── persistence/              # DO, Mapper (infra_data_source), Adapter
│   │   └── api/
│   │       ├── DataSourceAdminController.java
│   │       ├── dto/                      # DataSourceCreateRequest, ConnectionTestRequest
│   │       └── converter/
│   │
│   ├── config/                           # === 能力 3：参数配置（元数据 CRUD） ===
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   └── ConfigItem.java       # 配置项领域模型
│   │   │   └── repository/
│   │   │       └── ConfigRepository.java
│   │   ├── application/
│   │   │   ├── ConfigCommandService.java # 新增/修改配置
│   │   │   └── ConfigQueryService.java   # 查询配置，支持缓存
│   │   ├── infrastructure/
│   │   │   └── persistence/              # DO, Mapper (infra_config), Adapter
│   │   └── api/
│   │       ├── ConfigAdminController.java
│   │       └── dto/                      # ConfigUpdateRequest, ConfigResponse
│   │
│   └── support/                          # 工具支撑
│       ├── CryptoSupport.java            # 数据源密码/URL 的加解密接口
│       └── ConnectionTester.java         # 临时创建 DataSource 测试连通性（用完即毁）
│
└── (注意：本模块是底层业务支撑，不对外暴露 Facade 接口，仅由 realm-server 聚合暴露 Web API)
