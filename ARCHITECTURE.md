realm-cloud
================================================================================

License: Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
Java 17+ (https://adoptium.net/)
Spring Boot 3.x (https://spring.io/projects/spring-boot)
Spring Modulith 2.x (https://spring.io/projects/spring-modulith)

Realm Cloud — 以模块化单体为默认姿态、以量化阈值为演进约束的 SaaS 后端平台。
目标是成为世界级工程标准的 Java 系统架构参考实现。


================================================================================
1. 项目愿景与定位
   ================================================================================

Realm Cloud 不是一个“又一个后台管理系统”。它的目标是在架构形态、命名规范、
边界契约、技术标准四个维度上，同时对齐可验证的国际工程共识，成为可供其他团队
直接参照的世界级 Java 模块化单体架构样板。

“世界级”不意味着使用最多的技术热词，而意味着：每一个结构性决策都可以被追溯到
公开规范、被自动化工具验证、被同行在不依赖团队记忆的情况下理解。本项目在以下
方面贯彻这一原则：

- 架构形态有显式的 ADR 声明，而不是“默认微服务”或“先写代码再说”；
- 模块命名有国际术语依据，而不是技术分类标签或空泛缩写；
- 模块边界有构建期强制的契约（Spring Modulith verify()），而不是口头约定；
- 技术依赖有标准编号可查（RFC、W3C、NIST、OWASP），而不是“社区都这么用”。


================================================================================
2. 架构形态：模块化单体，按需提取微服务
   ================================================================================

2.1 决策
--------------------------------------------------------------------------------

默认姿态是模块化单体（Modular Monolith）；微服务是受量化阈值约束的提取动作，
不是默认起点。

这一决策不是折中，而是对 2014—2021 年“默认微服务”趋势的工程纠正。公开的生产
案例已经反复证明：在组织规模尚未达到微服务阈值时，过早的物理分布带来的协调税、
运维成本和网络延迟，会超过“独立部署”带来的收益。

- Amazon Prime Video 的 Video Quality Analysis 团队公开记录：将一个分布式
  serverless 流水线合并回单一进程后，运营成本降低约 90%。
- Shopify 从未将核心系统拆分为微服务，而是持续投资于 2.8M 行 Ruby 单体的
  模块化（通过 Packwerk 强制边界），并以此支撑每年黑色星期五的流量峰值。
- Sam Newman（《Monolith to Microservices》作者）在多次公开演讲中明确表达：
  “单体不是敌人”，“微服务不应该是默认选择”，并主张以模块化单体作为大多数
  系统的起点。

**工程共识的完整论据**：模块化单体不是某一家公司的孤立选择，而是 2026 年 Java
生态中，从 Shopify（2.8M 行 Ruby 单体 + Packwerk 强制边界）到 KakaoBank
（金融级生产案例），从 Spring 官方（Spring Modulith 2.0）到 JetBrains 工具链
（IntelliJ 对 Spring Modulith 的原生支持）共同指向的工程共识。这些组织与工具链
在完全不同的业务规模和监管环境下，独立收敛到了同一套架构姿态。

**ADR-001 与方案选型**：架构决策记录 ADR-001（模块分包方案）是本节决策的正式
载体，其中记录了明确的推荐倾向——以 Spring Modulith 2.0 为核心、ArchUnit 作为
补充的模块化单体方案。它不是某一条 ISO 条文，但它是被上述领先工程组织以公开
文档、架构决策记录和生产案例反复验证的默认演进方向。

该方案同时具备五项能力，这是它区别于其他方案的关键：

1. **一等模块抽象**：模块是代码结构的一等公民，而非事后通过命名约定模拟；
2. **声明式依赖白名单**：`@ApplicationModule(allowedDependencies)` 在代码中
   显式声明模块允许依赖哪些其他模块，而非依赖 code review 的自觉性；
3. **运行时事件基础设施**：模块间通过进程内领域事件协作，避免同步耦合；
4. **构建门禁强制**：`ApplicationModules.verify()` 在 CI 中自动验证边界，
   违反则构建失败；
5. **大规模企业生产验证**：已被 Shopify、KakaoBank 等组织在生产环境中采用。

五项能力中，任何一项单独存在都不足以构成“边界契约”；它们共同作用，才使
“边界”从文档中的文字变为构建期可验证的工程现实。

2.2 部署拓扑
--------------------------------------------------------------------------------

┌─────────────────────────────────────────────────────────┐
│                     realm-gateway                        │
│              （独立部署的 WebFlux API 网关）              │
└───────────────────────┬─────────────────────────────────┘
│
┌───────────────┴───────────────┐
│                               │
┌───────▼───────┐               ┌───────▼───────┐
│ realm-server  │               │ 未来提取的     │
│ （模块化单体   │               │ -server 微服务 │
│   唯一可运行   │               │ （按阈值触发） │
│   入口）       │               │               │
└───────────────┘               └───────────────┘

- 当前阶段：生产环境部署 realm-server（模块化单体） + realm-gateway（网关）。
- 演进阶段：当一个业务模块满足可验证的提取阈值时，将其从 realm-server 中提取
  为独立的 -server 应用；realm-server 继续承载尚未提取的模块，不会消失。
- 完全微服务阶段：只有当所有业务模块都提取完毕，realm-server 才退役。这不是
  默认终点，也不是大多数项目会到达的状态。

2.3 提取阈值（可验证信号，非主观感觉）
--------------------------------------------------------------------------------

信号            阈值                                          说明
--------------  --------------------------------------------  ------------------------------------------
团队规模        负责该模块的团队 >= 2 个披萨团队（5-9 人以上）  独立部署的收益来自团队自治；单团队不需要
物理隔离
变更频率冲突    该模块的发布节奏与其他模块持续冲突            如果每次发布都需要协调多个模块，物理隔离的
收益开始显现
独立伸缩需求    该模块的负载曲线与系统其他部分显著不同        例如会员服务在促销期间负载激增，而管理后台
负载平稳
组织规模        工程团队总规模接近或超过 100 人               单体代码库的协调开销开始超过分布式系统的
运维开销


================================================================================
3. 模块命名规范与依据
   ================================================================================

3.1 核心原则
--------------------------------------------------------------------------------

模块名必须揭示限界上下文（Bounded Context）的业务意图，使用无处不在的语言
（Ubiquitous Language），避免技术分类标签（module、system、infra）和空泛缩写。

DDD 对模块命名的根本要求是：模块名应当来自团队在讨论业务时实际使用的词，
而不是技术分层或营销名称。Eric Evans 在《Domain-Driven Design》中将 Module
定义为“传达模型中有意义的部分”，其名称应承载无处不在的语言，而不是技术角色。

Spring Modulith 的模块发现机制同样遵循这一原则：主应用类所在的包是“主包”，
主包下的直接子包就是模块；模块名直接来自包名，不需要额外的分类前缀。
ApplicationModules.of(Application.class).verify() 验证的正是包结构与声明约束
的一致性。

3.2 为什么去掉 module 前缀
--------------------------------------------------------------------------------

realm-module-* 中的 module 完成的是“业务 vs 技术”的分类功能。但这个功能已经
由 -spring-boot-starter 后缀承担：所有技术组件带该后缀，所有业务应用不带。

在模块化单体的语境中，module 另有特定含义——指限界上下文内部的逻辑模块，而不是
“可部署的业务应用”。用 module 作为业务应用的分类前缀，会造成语义混淆。因此，
业务模块直接使用领域名（realm-customer、realm-payment），技术组件保留
-spring-boot-starter 后缀。

3.3 技术组件命名：严格遵循 Spring Boot 官方规范
--------------------------------------------------------------------------------

Spring Boot 官方文档对 starter 命名有明确且唯一的规则：

    “All official starters follow a similar naming pattern; spring-boot-starter-*,
    where * is a particular type of application. Third party starters should not
    start with spring-boot-starter as it is reserved for official starters. A
    third-party starter for acme will be typically named acme-spring-boot-starter.”

本项目的所有技术组件严格遵循 {project}-{module}-spring-boot-starter 格式，例如：

本项目命名                              对应官方格式
--------------------------------------  ------------------------------------------
realm-web-spring-boot-starter           {project}-{module}-spring-boot-starter
realm-data-access-spring-boot-starter   同上
realm-openfeign-spring-boot-starter     同上

不使用 realm-spring-boot-starter-web 这类模仿官方前缀的格式，因为它违反了
“第三方 starter 不以 spring-boot-starter 开头”的规则。

3.4 业务模块命名：对齐国际术语与领先实践
--------------------------------------------------------------------------------

模块                     命名                 依据
-----------------------  -------------------  ----------------------------------------------
realm-workforce-identity Workforce Identity  Microsoft Azure Well-Architected Framework
明确区分 Workforce tenant（员工身份）与
External tenant / CIAM（客户身份），两者应使用
不同的用户存储、策略和身份提供者。
Workforce IAM 是这一上下文的国际标准术语。
realm-customer           Customer (CIAM)      Shopify、Stripe 等领先 SaaS 系统使用 Customer
指代外部付费/使用主体；Customer 是 CIAM 领域
的标准名词，无“会员等级”等歧义。
realm-payment            Payment              DDD 要求使用领域名词（Payment）而非动词
（Pay）；Payment 是支付处理上下文的通用标准
术语。
realm-analytics          Analytics / Reporting DDD 中只读分析上下文的常用术语；Analytics
比 Report 更具包容性，覆盖仪表盘、数据投影等
更广泛的只读分析能力。
realm-platform           Platform             Gartner 将 Platform Engineering 定义为“构建和
运营自助式 Internal Developer Platform (IDP)
的学科”。Platform 对齐这一术语，承载运维管理
能力（定时任务等），而非底层基础设施（infra）。
realm-devtools           Developer Tools      Developer Tools / DevEx 是主流工程组织中对代码
生成器、数据源管理等研发效率工具的标准归类；
devtools 不暗示底层基础设施。
realm-intelligence       Intelligence         以领域而非技术缩写命名。AI 是使能技术，
Intelligence 是更稳定的领域名称，不会随技术
热词过时。

关于 infra 的拆分：infra 在国际实践中指向计算、存储、网络等底层资源，而本项目的
realm-platform（定时任务管理）和 realm-devtools（代码生成器、数据源管理）是面向
运维和研发的内部平台能力，属于 Platform Engineering / IDP 范畴，不是底层基础设施。
拆分为两个模块，分别对齐“Platform Team 交付物”和“Developer Tooling”的术语。

关于 system 的修正：system 是空泛的技术词，不承载任何业务意图。本项目中该模块的
实际职责是内部员工的身份与访问管理，因此命名为 realm-workforce-identity，与
realm-customer（外部客户身份）形成清晰的 CIAM vs Workforce IAM 二元结构。


================================================================================
4. 模块职责与边界
   ================================================================================

4.1 框架组件（-spring-boot-starter）
--------------------------------------------------------------------------------

模块                                      职责（做什么）
----------------------------------------  ------------------------------------------
realm-core                                纯技术契约：PageResult、BusinessException、
SubjectContext、通用枚举
realm-utils                               通用工具类：日期、字符串、ID、JSON 辅助
realm-web-spring-boot-starter             HTTP 入站横切：RFC 9457 ProblemDetail 异常
映射、W3C Trace Context、日志脱敏、分页绑定、
CORS
realm-data-access-spring-boot-starter     数据访问基础设施：连接池、多数据源路由、
事务管理器基础
realm-stability-spring-boot-starter       可观测性（metrics/tracing/logging）+
稳定性保护（限流/熔断/幂等/分布式锁）
realm-openfeign-spring-boot-starter       OpenFeign 客户端治理：超时/幂等性感知重试、
上下文传播、指标埋点
realm-authorization-spring-boot-starter   授权决策：权限模型评估、方法级/URL 级授权
拦截、拒绝响应格式化
realm-audit-logging-spring-boot-starter   操作审计：@AuditLog 切面、异步事件、
SPI 持久化
realm-multitenancy-spring-boot-starter    租户上下文生命周期管理：解析 SPI、线程安全
上下文、请求边界清理
realm-messaging-spring-boot-starter       传输中立的消息抽象与 Binder SPI
realm-file-spring-boot-starter            文件数据交换抽象：注解模型、流式默认值、
导入校验、异步任务骨架
realm-object-storage-spring-boot-starter  对象存储统一抽象 + S3 协议默认实现
realm-config-client-spring-boot-starter   配置中心客户端接入：SPI、解密钩子、变更事件
realm-scheduling-spring-boot-starter      调度基础设施抽象：引擎 SPI、执行器生命周期、
可观测性
realm-feature-env-spring-boot-starter     特性环境流量染色与标签路由
realm-test                                测试资产总括：JUnit 5/AssertJ/Mockito 聚合、
realm 契约断言、ArchUnit/Testcontainers
可选扩展

模块                                      不做什么（边界）
----------------------------------------  ------------------------------------------
realm-core                                不含工具类、不含 Web 运行时逻辑、不含业务语义
realm-utils                               不含技术契约、不含业务语义、不含 Web 运行时
realm-web-spring-boot-starter             不含认证授权（realm-workforce-identity）、
不含出站调用（realm-openfeign）、不强制
OpenAPI 文档
realm-data-access-spring-boot-starter     不含任何 ORM 适配（MyBatis-Plus 和 JPA 是
平行的适配器）
realm-stability-spring-boot-starter        不含发布、容量、成本、混沌工程等 ops 能力
realm-openfeign-spring-boot-starter       不重复声明式客户端、负载均衡、服务发现
（由官方 starter 提供）
realm-authorization-spring-boot-starter   不含用户登录、令牌签发、审计记录写入
realm-audit-logging-spring-boot-starter   不含授权决策、不含审计查询/分析界面
realm-multitenancy-spring-boot-starter    不实现任何具体隔离策略（共享表/Schema/
独立库）；隔离模型由应用通过 SPI 注入
realm-messaging-spring-boot-starter       不含任何 Broker 客户端；Broker 适配器以
独立模块存在
realm-file-spring-boot-starter            不含文件存储（realm-object-storage）、
不含业务数据来源
realm-object-storage-spring-boot-starter  不含文件格式解析（realm-file）；厂商特有
SDK 为 optional
realm-config-client-spring-boot-starter   不实现配置中心服务端；不绑定单一产品
（Nacos/Apollo/Consul 为 optional）
realm-scheduling-spring-boot-starter      不重复 @Scheduled 基础能力；
Quartz/XXL-JOB 为 optional 引擎
realm-feature-env-spring-boot-starter     不含服务发现/负载均衡算法；不含 MQ/数据隔离
realm-test                                不含任何 src/main 生产级自动配置；下游必须
以 test 作用域引入

模块                                      依据
----------------------------------------  ------------------------------------------
realm-core                                技术契约与实现分离；realm-core 的编译依赖
只有 Jackson
realm-utils                               工具类与技术契约分离，避免所有模块被动继承
工具库
realm-web-spring-boot-starter             ADR-002
realm-data-access-spring-boot-starter     Spring Boot 将 spring-boot-starter-jdbc 与
spring-boot-starter-data-jpa 分层交付的惯例
realm-stability-spring-boot-starter       可观测性与保护共享同一指标基础设施和运维
消费者，合并是内聚的
realm-openfeign-spring-boot-starter       RFC 9110 幂等性语义：仅幂等方法可安全自动
重试
realm-authorization-spring-boot-starter   NIST SP 800-53 AC 控制族（Access Control）
realm-audit-logging-spring-boot-starter   NIST SP 800-53 AU 控制族（Audit and
Accountability）；AC 与 AU 是独立的控制族，
不应混在同一模块中
realm-multitenancy-spring-boot-starter    租户上下文的生命周期与数据隔离策略是两个
关注点；AWS / Azure 均强调隔离模型是按组件
决定的决策
realm-messaging-spring-boot-starter       Spring Cloud Stream 的 Binder SPI 设计：
核心抽象与具体中间件解耦
realm-file-spring-boot-starter            OWASP 文件上传指南：不信任客户端提供的内容；
流式默认值消除 OOM 风险
realm-object-storage-spring-boot-starter  S3 协议已成为对象存储事实标准；统一抽象 +
可替换实现
realm-config-client-spring-boot-starter   Spring Cloud PropertySourceLocator 机制
realm-scheduling-spring-boot-starter      Spring TaskScheduler 术语体系；Quartz 的
Job/Trigger/Scheduler 三层分离
realm-feature-env-spring-boot-starter     Spring Cloud LoadBalancer 的
HintBasedServiceInstanceListSupplier 机制
realm-test                                测试资产与生产代码分离；作用域由下游 POM
控制

4.2 业务模块
--------------------------------------------------------------------------------

模块                     职责
-----------------------  ------------------------------------------
realm-workforce-identity 内部员工的身份与访问管理：用户、部门、角色、菜单、
OAuth 客户端与令牌
realm-customer           外部客户身份与会员管理：客户资料、等级、积分、签到、
地址、标签
realm-payment            支付处理、交易流水、对账
realm-analytics          只读分析：报表、仪表盘、数据投影
realm-platform           运维管理：定时任务管理、服务器信息等
realm-devtools           研发工具：代码生成器、数据源管理、参数配置
realm-intelligence       AI 能力编排：Agent 管理、智能决策、推理集成

模块                     不做什么
-----------------------  ------------------------------------------
realm-workforce-identity 不含外部客户身份（realm-customer）；不含授权决策执行
（realm-authorization 消费其数据）
realm-customer           不含内部员工身份；不承担 CIAM 之外的业务逻辑
realm-payment            不承载订单/交易模块的业务逻辑
realm-analytics          不写入业务数据；不做事务性操作
realm-platform           不含代码生成器、数据源管理等研发工具（realm-devtools）；
不含底层基础设施（连接池、消息队列在 framework 层）
realm-devtools           不含运维管理（realm-platform）；只在开发/管理场景使用
realm-intelligence       不实现模型推理本身；不绑定单一模型提供商

4.3 独立应用
--------------------------------------------------------------------------------

模块           职责
-------------  ------------------------------------------
realm-gateway  API 网关：动态路由、JWT 验证、粗粒度授权、流量治理、
可观测性入口
realm-server   模块化单体的唯一可运行入口：组装所有未提取的业务模块，
触发 Spring Modulith 边界验证

模块           部署形态
-------------  ------------------------------------------
realm-gateway  独立部署（WebFlux）；不与 Servlet 栈的 realm-web /
realm-authorization 混用
realm-server   当前阶段的生产部署单元；部分模块提取后承载剩余模块


================================================================================
5. -api / -server 拆分策略
   ================================================================================

5.1 默认不拆
--------------------------------------------------------------------------------

默认不拆分 -api / -server。在模块化单体中，Spring Modulith 的隔离应在包级别
完成，而不是用 Maven 模块级拆分模拟微服务。

-api / -server 是“已经确定要提取微服务”时的预置结构，不是模块化单体的默认形态。
拆分成本不在“多写两个 POM”，而在依赖方向的管理：-api 不能依赖 -server，其他
模块只能依赖 -api，很容易产生模块间 -api 的循环依赖。

5.2 判断标准
--------------------------------------------------------------------------------

如果某个模块的 API 被两个或以上其他业务模块依赖，就拆 -api；否则保持单 Maven
模块，用 facade + internal 包结构。

当前架构中，没有模块被确定为必须拆分。所有业务模块保持单 Maven 模块，内部用：

    realm-{module}/
    └── src/main/java/cn/realm/cloud/{module}/
        ├── {Module}Facade.java      # 对外门面（如有）
        ├── {Module}Dto.java         # 跨模块 DTO（如有）
        ├── package-info.java        # @ApplicationModule(allowedDependencies = {...})
        ├── events/                  # 对外事件（@NamedInterface("events")）
        └── internal/                # 以下全部私有
            ├── domain/
            ├── application/
            ├── infrastructure/
            └── api/

如果未来某个模块需要被多个其他模块依赖，再将其拆分为 -api / -server，不需要
重写业务逻辑——只是把根包下的 Facade / Dto / events/ 机械地提取到 -api 子模块。


================================================================================
6. 技术标准与依赖依据
   ================================================================================

标准                                     编号
---------------------------------------  ------------------------------------------
Problem Details for HTTP APIs            IETF RFC 9457
Trace Context                            W3C Trace Context Level 3
Access Control (AC) 控制族               NIST SP 800-53
Audit and Accountability (AU) 控制族     NIST SP 800-53
认证（V2）                               OWASP ASVS
错误处理与日志记录（V7）                 OWASP ASVS
Workforce vs CIAM                        Microsoft Azure Well-Architected Framework
Platform Engineering / IDP               Gartner
模块结构验证                             Spring Modulith verify()
显式模块依赖与具名接口                   Spring Modulith @ApplicationModule /
@NamedInterface

标准                                     在本项目中的落点
---------------------------------------  ------------------------------------------
Problem Details for HTTP APIs            realm-web 的默认错误响应格式
Trace Context                            realm-web / realm-stability 的跨服务追踪
传播
Access Control (AC) 控制族               realm-authorization 的职责边界
Audit and Accountability (AU) 控制族     realm-audit-logging 的职责边界
认证（V2）                               realm-workforce-identity /
realm-customer 的登录与凭证处理
错误处理与日志记录（V7）                 realm-web 的异常映射、realm-audit-logging
的日志脱敏
Workforce vs CIAM                        realm-workforce-identity 与
realm-customer 的分离
Platform Engineering / IDP               realm-platform 的命名与定位
模块结构验证                             所有业务模块的 CI 门禁
显式模块依赖与具名接口                   每个模块的 package-info.java

标准                                     关键原文
---------------------------------------  ------------------------------------------
Problem Details for HTTP APIs            “defines a ‘problem detail’ to carry
machine-readable details of errors in HTTP
response content to avoid the need to define
new error response formats for HTTP APIs”
Trace Context                            “defines standard HTTP headers and a value
format to propagate context information that
enables distributed tracing scenarios”
Access Control (AC) 控制族               AC 族涵盖账户管理、最小权限、职责分离、
远程访问等
Audit and Accountability (AU) 控制族     AU 族涵盖日志记录、监控、审计记录留存；
AC 与 AU 是独立的控制族，不应混在同一
模块中
认证（V2）                               V2 要求认证机制抵抗凭证填充、暴力破解和
绕过攻击
错误处理与日志记录（V7）                 V7 要求错误消息不泄漏敏感信息，安全事件
被记录
Workforce vs CIAM                        “在 SaaS 工作负载的上下文中，有两种不同
的身份识别类型”；Workforce tenant 用于员工，
External tenant 用于客户
Platform Engineering / IDP               “Platform engineering is the discipline of
building and operating self-service internal
developer platforms (IDPs) for software
delivery and life cycle management”
模块结构验证                             ApplicationModules.of(Application.class)
.verify() 验证：模块间无循环依赖；只能
通过 API 包访问其他模块；
allowedDependencies 之外的外部依赖被拒绝
显式模块依赖与具名接口                   allowedDependencies 声明允许的依赖；
module::API 语法允许对具名接口的细粒度依赖


**边界应成为契约，而非软约定**（boundaries become contracts rather than soft
conventions）。

这句话是社区在讨论 Spring Modulith 时形成的工程共识表述，并非 Spring 官方文档的
逐字原文。本项目的技术实现直接依赖 Spring Modulith 的以下机制，使这一表述成为
可执行的工程现实：

- @ApplicationModule(allowedDependencies = {...})：在代码中显式声明模块允许依赖
  哪些其他模块；
- ApplicationModules.of(Application.class).verify()：在 CI 中自动验证代码结构是否
  遵守声明，违反则构建失败；
- @NamedInterface：将跨模块契约限定在具名接口（如 events）上，实现细粒度暴露。

这些机制共同使“边界”从文档中的文字，变为构建期可验证的契约。


================================================================================
7. 边界契约与自动化验证
   ================================================================================

7.1 每个模块的 package-info.java
--------------------------------------------------------------------------------

    @org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "realm-core",                     // 技术契约（OPEN，通常无需列出）
            "realm-workforce-identity :: api", // 依赖其具名接口
            "realm-audit-logging :: events"    // 仅消费事件，不调用 API
        }
    )
    package cn.realm.cloud.customer;

allowedDependencies 是显式白名单：未列出的模块依赖将被 verify() 拒绝。这使模块间
的耦合从“隐式 import”变为“显式声明”。

7.2 验证的硬性规则
--------------------------------------------------------------------------------

ApplicationModules.verify() 执行以下规则，任何违反均导致构建失败：

1. 模块间无循环依赖：依赖必须形成有向无环图。
2. 只能通过 API 包访问：对 internal/ 包中类型的任何引用被拒绝。
3. allowedDependencies 之外的外部依赖被拒绝（如果配置了 allowedDependencies）。

7.3 补充：ArchUnit 规则
--------------------------------------------------------------------------------

Spring Modulith 可选集成 jMolecules ArchUnit 库，自动触发 DDD 验证规则。本项目在
realm-test 中提供 ArchUnit 依赖（optional），用于补充 Modulith 未覆盖的规则，例如：

- 禁止 Controller 直接依赖 Repository；
- 禁止 domain 层出现 Web 或 Persistence 注解；
- 验证 facade + internal 包结构。


================================================================================
8. 参考标准与原文索引
   ================================================================================

#   标准 / 来源                              原文关键句
--  ---------------------------------------  ------------------------------------------
1   Spring Boot — Creating Your Own Starter  “Third party starters should not start
with spring-boot-starter as it is reserved
for official starters. A third-party starter
for acme will be typically named
acme-spring-boot-starter.”
https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.custom-starter

2   Spring Modulith — Verifying Application  “We can verify whether our code arrangement
Module Structure                         adheres to the intended constraints by
calling the verify() method on our
ApplicationModules instance.”
https://docs.spring.io/spring-modulith/reference/verification.html

3   Spring Modulith — @ApplicationModule /   “allowedDependencies — List the names of
@NamedInterface                          modules that the module is allowed to depend
on… NamedInterfaces need to be separated by
a double colon ::.”
https://docs.spring.io/spring-modulith/reference/fundamentals.html#modules.explicit-dependencies

4   IETF RFC 9457 — Problem Details for      “This document defines a ‘problem detail’
HTTP APIs                                to carry machine-readable details of errors
in HTTP response content to avoid the need
to define new error response formats for
HTTP APIs.”
https://datatracker.ietf.org/doc/rfc9457/

5   W3C Trace Context Level 3                “This specification defines standard HTTP
headers and a value format to propagate
context information that enables distributed
tracing scenarios.”
https://w3c.github.io/trace-context/

6   NIST SP 800-53 — AC / AU Control         AC 族：Access Control（账户管理、最小权限、
Families                                 职责分离）；AU 族：Audit and Accountability
（日志记录、监控、审计记录留存）。两族独立。
https://csrc.nist.gov/publications/detail/sp/800-53/rev-5/final

7   OWASP ASVS — V2 / V7                     V2 Authentication：“authentication mechanisms
resist credential stuffing, brute force, and
bypass attacks”；V7 Error Handling and
Logging：“error messages do not leak
sensitive information and that security
events are logged”。
https://owasp.org/www-project-application-security-verification-standard/

8   Microsoft Azure Well-Architected —       “在 SaaS 工作负载的上下文中，有两种不同的
SaaS Identity                            身份识别类型”；Workforce tenant 用于员工，
External tenant（CIAM）用于客户。
https://learn.microsoft.com/en-us/azure/well-architected/saas/identity-access

9   Gartner — Platform Engineering / IDP      “Platform engineering is the discipline of
building and operating self-service internal
developer platforms (IDPs) for software
delivery and life cycle management.”
https://www.cncf.io/

10  Amazon Prime Video — 从分布式合并回单体    Video Quality Analysis 团队将分布式
serverless 流水线合并回单一进程，运营成本
降低约 90%。
https://www.primevideotech.com/video-quality-analysis

11  Shopify — 模块化单体                      2.8M 行 Ruby 单体通过 Packwerk 强制模块
边界，支撑黑色星期五流量峰值。
https://shopify.engineering/

12  Sam Newman — Monolith to Microservices    “The monolith is not the enemy”；
“microservices should not be the default
choice”；主张以模块化单体作为大多数系统
的起点。
O'Reilly, 2019

13  Eric Evans — Domain-Driven Design         Module 应承载无处不在的语言
（Ubiquitous Language）；Bounded Context
拥有独立的模型和语言。
Addison-Wesley, 2003


================================================================================
9. 一句话总结
   ================================================================================

Realm Cloud 的架构目标是：以模块化单体为默认姿态，以 Spring Modulith verify()
为边界契约的强制手段，以 DDD 限界上下文和 CIAM/Workforce 国际术语为命名依据，
以 RFC / W3C / NIST / OWASP 为技术依赖的可追溯来源，成为“每一个决策都可被外部
标准验证”的世界级 Java 系统架构参考。
