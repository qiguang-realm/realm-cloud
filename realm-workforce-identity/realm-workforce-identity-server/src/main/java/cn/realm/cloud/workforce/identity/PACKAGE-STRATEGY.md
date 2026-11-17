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
