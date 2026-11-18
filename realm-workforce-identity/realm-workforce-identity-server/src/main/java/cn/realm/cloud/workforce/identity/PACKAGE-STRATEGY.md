📁 realm-workforce-identity-server 📁 详细包结构全景

realm-workforce-identity-server/
└── src/main/java/cn/realm/cloud/workforce/identity/
│
├── package-info.java                
│   // [架构边界声明] 核心注解：@ApplicationModule(allowedDependencies = {...})
│   // 职责：声明本模块（业务实现）允许依赖的外部模块（如 realm-core, realm-web 等）。
│   // 红线：严禁在此声明 realm-authorization 或 realm-customer，避免循环依赖与跨域耦合。
│
├── IdentityFacadeImpl.java          
│   // [对外唯一门面] 放在根包，但模块外部通过 Maven 隔离无法直接 new 它。
│   // 职责：实现 `realm-workforce-identity-api` 定义的 IdentityFacade 接口。
│   // 约束：只能进行简单的“参数校验 -> 调用 internal 应用服务 -> 实体转 DTO”编排，
│   //       绝不允许在此处写具体的业务逻辑或直接访问数据库。
│
└── internal/                        // 🚨 [私有实现区] Modulith verify() 会拦截任何外部模块对本包的引用
│
├── config/                      // [模块级配置中心]
│   │   // 职责：存放整个模块共享的 Spring 配置类。
│   ├── IdentityConfig.java      // 例如：@EnableMethodSecurity 开启、密码策略 Bean、审计切面配置。
│   └── OAuth2Config.java        // 例如：JWT 签名密钥配置、Token 有效期策略、认证服务器端点配置。
│   // ⚠️ 注意：如果 realm-server 无法扫描到 internal 包，需在 realm-server 启动类
│   //           显式添加 @ComponentScan(basePackages = "...internal.config")。
│
├── infrastructure/              // [跨子域共享的基础设施]
│   │   // 职责：存放被 user, role, oauth 等多个子域共同依赖的技术组件。
│   │   // 为什么要提升到这里？为了避免 oauth 子域去直接依赖 user 子域的 infra 实现（导致循环依赖）。
│   ├── persistence/             // 通用持久化配置（如全局 MyBatis 拦截器、租户 ID 自动填充）。
│   └── security/                // 跨子域安全实现。
│       └── PasswordEncoderImpl.java  // 密码加密工具。user 管理用户时用，oauth 校验密码时也用。
│
├── user/                        // === 垂直子域 1：用户管理（聚合根：User） ===
│   │   // 领域驱动设计（DDD）的限界上下文边界。
│   │   // 依赖方向：api -> application -> domain <- infrastructure
│   │
│   ├── domain/                  // [核心领域层] 纯 Java 代码，零 Spring/ORM 依赖。
│   │   ├── model/
│   │   │   ├── User.java        // [充血模型] 聚合根。包含核心业务行为：changePassword()、disable()。
│   │   │   └── UserStatus.java  // [枚举] 状态机定义（0-正常，1-停用），避免魔法值。
│   │   ├── repository/
│   │   │   └── UserRepository.java  // [出站端口/Port] 领域层只定义接口，不关心底层是 MySQL 还是 Redis。
│   │   └── service/
│   │       └── PasswordDomainService.java // [领域服务] 处理跨聚合根的复杂密码规则（如校验历史密码不能重复）。
│   │
│   ├── application/             // [应用层] 用例编排，事务的绝对边界（@Transactional）。
│   │   ├── UserCommandService.java  // [写操作] 注册、修改、分配角色；内部校验后，发布 `UserRegisteredEvent`。
│   │   └── UserQueryService.java    // [读操作] 分页查询、详情查询；根据 CQRS 理念，可绕过 Domain 直接查 DO 组装 DTO 提升性能。
│   │
│   ├── infrastructure/          // [基础设施层] 该子域专属的技术实现。
│   │   └── persistence/
│   │       ├── MybatisUserRepositoryAdapter.java // [适配器] 实现 UserRepository 端口，调用 Mapper。
│   │       ├── UserDO.java      // [持久化对象] 严格映射 `iam_user` 表结构，内部流转，绝不暴露给前端。
│   │       ├── UserMapper.java  // [ORM 接口] MyBatis-Plus 的 BaseMapper。
│   │       └── UserPersistenceConverter.java // [转换器] 负责 User(领域模型) 与 UserDO(数据库对象) 的互相转换。
│   │
│   └── api/                     // [入站适配层] HTTP REST 接口。
│       ├── UserAdminController.java // 路径：/admin/iam/user。处理 HTTP 入参校验，调用 Command/Query Service。
│       ├── dto/                 // [传输对象] 仅供前端使用的 Request/Response，与数据库实体严格隔离。
│       └── converter/           // [转换器] 负责 DTO 与 Command/Query 对象的互相转换（可用 MapStruct）。
│
├── role/                        // === 垂直子域 2：角色管理（聚合根：Role） ===
│   ├── domain/                  // 包含角色实体、权限分配规则。
│   ├── application/             // 角色 CRUD、给用户分配角色。
│   ├── infrastructure/          // `iam_role` 表的持久化适配器。
│   └── api/                     // `/admin/iam/role` 接口。
│
├── permission/                  // === 垂直子域 3：权限与菜单（聚合根：Permission/Menu） ===
│   ├── domain/                  // 树形结构的组装逻辑、权限校验点。
│   ├── application/             // 菜单树构建、权限点查询（供 ProfileController 调用）。
│   ├── infrastructure/          // `iam_menu`、`iam_role_menu` 表的持久化。
│   └── api/                     // `/admin/iam/menu` 接口。
│
├── organization/                // === 垂直子域 4：组织架构（聚合根：Department/Position） ===
│   ├── domain/                  // 部门树的领域规则（如：父部门不可被删除）。
│   ├── application/             // 部门与岗位的 CRUD。
│   ├── infrastructure/          // `iam_department` 表持久化。
│   └── api/                     // `/admin/iam/department` 接口。
│
└── oauth/                       // === 垂直子域 5：OAuth2 认证授权（聚合根：OAuthClient/Token） ===
├── domain/                  // Token 的黑名单逻辑、客户端权限验证。
├── application/             // 令牌签发（TokenService）、令牌刷新、强制下线（踢人）。
├── infrastructure/          // JWT 签名生成器、Redis Token 存储适配器。
└── api/                     // `/oauth/token`、`/auth/login` 接口。


cn.realm.cloud.workforce.identity.internal.user
├── package-info.java                           // User 限界上下文总说明
├── domain                                      // 领域层（最内层，无外部依赖）
│   ├── package-info.java
│   ├── model                                   // 聚合根、实体、值对象
│   │   ├── package-info.java
│   │   ├── User.java                           // User聚合根
│   │   ├── UserStatus.java                     // 枚举：启用/停用
│   │   └── vo                                  // 值对象
│   │       ├── package-info.java
│   │       ├── Username.java
│   │       └── MobileMasked.java
│   ├── event                                   // 领域事件
│   │   ├── package-info.java
│   │   ├── UserCreatedEvent.java
│   │   ├── UserStatusChangedEvent.java
│   │   └── UserDeletedEvent.java
│   ├── repository                              // 领域仓储接口（仅接口，实现放在adapter/out）
│   │   └── package-info.java
│   └── service                                 // 领域服务（跨聚合、无状态领域逻辑）
│       └── package-info.java
├── application                                 // 应用层：用例、命令、查询、应用服务
│   ├── package-info.java
│   ├── command                                 // 应用命令（写操作意图）
│   │   └── package-info.java
│   ├── query                                   // 应用查询对象（读操作意图）
│   │   └── package-info.java
│   ├── dto                                     // 应用层内部视图（领域→应用层输出，不是对外HTTP/RPC DTO）
│   │   └── package-info.java
│   └── service                                 // 应用服务（UserService / UserRoleService / UserPositionService）
│       ├── package-info.java
│       ├── UserService.java
│       ├── UserRoleService.java
│       └── UserPositionService.java
└── adapter                                     // 适配器层：隔离外部技术（入站+出站）
├── package-info.java
├── in                                      // 【入站适配器 inbound】外部 → 本系统
│   ├── package-info.java
│   ├── client-app                          // C端 APP/小程序自助入口：只能操作自己，无管理员权限
│   │   ├── package-info.java
│   │   ├── UserAppController.java
│   │   ├── converter
│   │   │   └── package-info.java
│   │   │   └── UserAppConverter.java
│   │   └── dto
│   │       ├── package-info.java
│   │       ├── UserProfileUpdateRequest.java
│   │       └── UserProfileResponse.java
│   ├── web
│   │   ├── package-info.java
│   │   └── admin                           // 管理后台Web入口：RBAC管理员，可管理任意用户
│   │       ├── package-info.java
│   │       ├── UserAdminController.java
│   │       ├── converter
│   │       │   └── package-info.java
│   │       │   └── UserAdminConverter.java
│   │       └── dto
│   │           ├── package-info.java
│   │           ├── UserCreateRequest.java
│   │           ├── UserUpdateRequest.java
│   │           ├── UserPageRequest.java
│   │           ├── UserResponse.java
│   │           ├── UserStatusUpdateRequest.java
│   │           └── UserRoleAssignRequest.java
│   └── rpc                                 // 服务间RPC入站适配器：实现独立api模块的UserApi契约，供其他微服务调用
│       ├── package-info.java
│       ├── UserApiController.java
│       ├── converter
│       │   └── package-info.java
│       │   └── UserRpcConverter.java
│       └── dto
│           └── package-info.java
└── out                                     // 【出站适配器 outbound】本系统 → 外部基础设施
├── package-info.java
└── persistence                         // 持久化实现：MyBatis/ORM、DO、Mapper、仓储实现类
├── package-info.java
├── UserDO.java
├── mapper
│   └── package-info.java
└── repository
└── package-info.java


adapter/
├── inbound/
│   ├── web/
│   │   ├── admin/          # B端管理后台
│   │   ├── app/            # C端App/小程序
│   │   └── external/       # 对外合作伙伴/开放平台
│   ├── internal/           # 模块间/服务间调用入口
│   └── job/                # 调度任务入口
└── outbound/
├── persistence/        # 数据库持久化
├── remote/             # 调用外部服务
└── messaging/          # 消息发布


// adapter/inbound/web/admin
@Tag(name = "Identity-Admin", description = "身份域｜B端管理后台接口")
@RequestMapping("/identity/admin")
public class IdentityAdminController {}

// adapter/inbound/web/app
@Tag(name = "Identity-App", description = "身份域｜C端App接口")
@RequestMapping("/identity/app")
public class IdentityAppController {}

// adapter/inbound/web/external
@Tag(name = "Identity-External", description = "身份域｜开放平台第三方接口")
@RequestMapping("/identity/external")
public class IdentityExternalController {}

// adapter/inbound/internal
@Tag(name = "Identity-Internal", description = "身份域｜微服务内部调用接口，禁止外部访问")
@RequestMapping("/identity/internal")
public class IdentityInternalController {}


// adapter/inbound/web/app
@RestController
@RequestMapping("/identity/app/users")
@Tag(name = "Identity-App", description = "身份域｜C端App接口")
public class UserAppController {}

// adapter/inbound/web/external
@RestController
@RequestMapping("/identity/external/users")
@Tag(name = "Identity-External", description = "身份域｜开放平台第三方接口")
public class UserExternalController {}

// adapter/inbound/internal
@RestController
@RequestMapping("/identity/internal/users")
@Tag(name = "Identity-Internal", description = "身份域｜微服务内部调用接口，禁止外部访问")
public class UserInternalController {}


// adapter/inbound/web/admin
@RestController
@RequestMapping("/identity/admin")
@Tag(name = "Identity-Admin", description = "身份域｜B端管理后台接口")
public class IdentityAdminController {
// 放域级别的接口，例如 /identity/admin/settings，不是 users
}


📥 adapter/inbound — 外部如何调用本系统

adapter/inbound/
│
├── web/                                   # HTTP 入站（Spring MVC）
│   ├── admin/                             # B端管理后台
│   │   ├── UserAdminController.java
│   │   ├── converter/
│   │   │   └── UserAdminConverter.java
│   │   └── dto/
│   │       ├── UserCreateRequest.java
│   │       ├── UserUpdateRequest.java
│   │       ├── UserPageRequest.java
│   │       ├── UserResponse.java
│   │       └── UserStatusUpdateRequest.java
│   │
│   ├── app/                               # C端App/小程序
│   │   ├── UserAppController.java
│   │   ├── converter/
│   │   │   └── UserAppConverter.java
│   │   └── dto/
│   │       ├── UserProfileUpdateRequest.java
│   │       └── UserProfileResponse.java
│   │
│   └── external/                          # 对外合作伙伴/开放平台
│       ├── UserExternalController.java
│       ├── converter/
│       │   └── UserExternalConverter.java
│       └── dto/
│           ├── UserExternalRequest.java
│           └── UserExternalResponse.java
│
├── internal/                              # 模块间/服务间调用入口（HTTP RPC）
│   ├── UserApiController.java             # 实现 -api 模块的 UserApi 接口
│   └── converter/
│       └── UserRpcConverter.java
│   # 注意：DTO 已在 -api 模块定义，这里不再有 dto/ 子包
│
└── job/                                   # 调度任务入口（XXL-JOB/Quartz/Spring @Scheduled）
├── UserCleanupJob.java                # 用户数据清理任务
└── UserSyncJob.java                   # 用户数据同步任务

📤 adapter/outbound — 本系统如何调用外部资源

adapter/outbound/
│
├── persistence/                           # 数据库持久化（MyBatis-Plus）
│   ├── do/                                # Data Object 数据库实体
│   │   ├── UserDO.java
│   │   ├── UserRoleDO.java
│   │   └── UserPositionDO.java
│   │
│   ├── mapper/                            # MyBatis Mapper 接口
│   │   ├── UserMapper.java
│   │   ├── UserRoleMapper.java
│   │   └── UserPositionMapper.java
│   │
│   ├── converter/                         # DO ↔ 领域模型/内部DTO 转换器
│   │   └── UserPersistenceConverter.java
│   │
│   └── repository/                        # 仓储实现（实现 domain.repository 接口）
│       ├── UserRepositoryImpl.java
│       ├── UserRoleRepositoryImpl.java
│       └── UserPositionRepositoryImpl.java
│
├── remote/                                # 调用外部服务（Feign/WebClient）
│   ├── client/                            # Feign 客户端接口
│   │   └── UserServiceClient.java         # 继承 -api 模块定义的 Feign 契约
│   │
│   ├── dto/                               # 远程调用的响应 DTO（外部契约镜像）
│   │   └── RemoteUserResponse.java
│   │
│   └── converter/                         # 外部响应 → 内部模型转换
│       └── RemoteUserConverter.java
│
└── messaging/                             # 消息发布（RocketMQ/Kafka）
├── producer/                          # 消息生产者
│   └── UserEventProducer.java
│
├── message/                           # 消息体定义
│   └── UserCreatedMessage.java
│
└── converter/                         # 领域事件 → 消息体转换
└── UserMessageConverter.java

在测试源码目录建一个临时的启动类，专门用于本地开发：

realm-workforce-identity-server/
└── src/
├── main/
│   └── java/...          <-- 这里绝对不能有 @SpringBootApplication
└── test/
├── java/cn/realm/cloud/workforce/identity/
│   └── LocalIdentityTestApplication.java  <-- 放在这里
└── resources/
└── application-local.yml              <-- 专属测试配置


