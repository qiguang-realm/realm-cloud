📁 realm-test 详细包结构全景

realm-test/
└── src/main/java/cn/realm/cloud/framework/test/
│
├── package-info.java             # [必须] 模块全局定位与架构红线声明
│
├── assertion/                    # 自定义断言工具（基于 AssertJ）
│   ├── package-info.java
│   ├── ApiResponseAssert.java    # 验证统一响应体（code, message, data）
│   ├── PageResultAssert.java     # 验证分页结果（total, pageNo, list）
│   └── BusinessExceptionAssert.java # 验证业务异常（错误码、提示信息）
│
├── arch/                         # 架构验证规则（强制边界门禁）
│   ├── package-info.java
│   ├── ModulithVerifyBase.java   # Spring Modulith 边界验证基类/工具
│   └── ArchUnitRules.java        # 通用 ArchUnit 规则（如 Controller 不能依赖 Repository）
│
├── base/                         # 测试基类
│   ├── package-info.java
│   ├── BaseUnitTest.java         # 纯单元测试基类（自动开启 MockitoExtension）
│   └── BaseIntegrationTest.java  # 集成测试基类（@SpringBootTest + 统一配置）
│
├── fixture/                      # 测试数据生成与构造
│   ├── package-info.java
│   ├── PodamUtils.java           # 基于 Podam 的随机 POJO 生成工具
│   └── TestDataBuilder.java      # 泛型测试数据构造器接口/基类
│
├── mock/                         # Mock 与测试替身
│   ├── package-info.java
│   ├── MockSubjectContext.java   # SubjectContext 的测试实现（用于注入当前用户）
│   └── MockTenantContext.java    # TenantContext 的测试实现
│
└── container/                    # 测试容器支撑（Testcontainers）
├── package-info.java
└── ContainerConfig.java      # 数据库/Redis 的 Testcontainers 基础配置
