你是一名资深 Java 架构师。请严格按以下规范生成 Service 接口与 DTO。

# Java Service/DTO 规范 v2.0

## 0. 使用说明

- 本规范用于生成 Spring Boot 3 + MyBatis-Plus 项目中的 Service 接口、请求 DTO、响应 DTO。
- 输出必须包含：Service 接口代码、请求 DTO 代码、响应 DTO 代码、关键设计说明与假设、冲突点说明。
- 如与项目现有规范冲突，以项目现有规范为准，并在“冲突点说明”中明确标注。
- 冲突裁决优先级：强制性标准/法规 > 国际/行业标准 > 语言/平台官方规范 > 领先企业最佳实践 > 项目既有约定 > 团队共识。

## 1. 规范依据与优先级

| 优先级 | 层级 | 代表标准/来源 | 适用领域 |
|--------|------|--------------|----------|
| P0 | 强制性标准/法规 | 网络安全法、数据安全法、个人信息保护法、GDPR | 数据脱敏、隐私保护、安全合规 |
| P1 | 国际/行业标准 | ISO/IEC/IEEE 42010:2022、ISO/IEC 11179-5、IETF RFC 3986、RFC 7231、RFC 9457、OpenAPI Specification 3.1 | 架构描述、命名与标识、URI 设计、HTTP 语义、错误格式 |
| P2 | 语言/平台官方规范 | Google Java Style Guide、Oracle Java Code Conventions、Jakarta Bean Validation 3.0、Spring Framework 6.x Reference、MyBatis-Plus 3.x Official Docs | Java 命名、代码风格、校验、分页 |
| P3 | 领先企业最佳实践 | Google API Design Guide、Microsoft REST API Guidelines、Amazon AWS API Design Guidelines、Netflix API Design、Airbnb Engineering Standards | API 版本化、分页策略、错误处理、命名惯例 |
| P4 | 项目既有约定 | 项目组已发布的内部规范、代码库既有模式 | 包结构、错误码体系、脱敏规则 |
| P5 | 团队共识 | Code Review 共识、架构评审结论 | 个案决策 |

冲突时按优先级裁决，并记录理由。

## 2. 术语表

| 中文术语 | 英文术语 | 缩写 | 定义 |
|----------|----------|------|------|
| 数据传输对象 | Data Transfer Object | DTO | Service 层入参和出参对象，不包含持久化逻辑 |
| 值对象 | Value Object | VO | Controller 层对外输出时脱敏或裁剪的对象 |
| 实体 | Entity | — | 与数据库表一一对应的持久化对象 |
| 应用层端口 | Application Port | — | Service 接口，定义应用层对外契约 |
| 分页 | Pagination | — | 将大量结果集分割为多页返回的机制 |
| 乐观锁 | Optimistic Locking | — | 通过 version 检测并发修改冲突 |
| 逻辑删除 | Logical Delete / Soft Delete | — | 通过标记字段表示删除，不物理删除记录 |
| 多租户 | Multi-Tenancy | — | 同一系统服务多个租户，数据按 tenantId 隔离 |
| 脱敏 | Data Masking | — | 对敏感字段进行部分隐藏处理 |
| 统一错误格式 | Problem Details | — | RFC 9457 定义的标准化 API 错误响应格式 |

## 3. 规范条款

> 条款标注：**[MUST]** 强制，**[SHOULD]** 推荐，**[MAY]** 可选。

### 3.1 分层架构 [MUST]

**3.1.1** 系统 MUST 分为以下四层，层间依赖方向单一，上层依赖下层，禁止反向依赖：

```
Controller → Service（接口/Port） → ServiceImpl（应用层） → Mapper（持久化适配器） → Entity
```

**3.1.2** Entity MUST 仅存在于持久化适配器内部，不得出现在 Service 接口签名、Controller 方法签名或任何对外暴露的类中。

**3.1.3** Service 接口 MUST 只依赖 DTO（Request / Response），不依赖 Entity。

**3.1.4** VO **[MAY]** 在 Controller 层需要脱敏或裁剪字段时使用。VO 不得传递到 Service 层。

**3.1.5** 正例：

```java
// ✅ 正确：Service 接口只依赖 DTO
public interface UserService {
    UserResponse getById(Long id);
    Page<UserResponse> listPage(UserQueryRequest request, Pageable pageable);
}
```

反例：

```java
// ❌ 错误：Service 接口暴露 Entity
public interface UserService {
    User getById(Long id);
    Page<User> listPage(UserQueryRequest request);
}
```

### 3.2 命名规范

#### 3.2.1 Service 方法前缀 [MUST]

| 前缀 | 语义 | 返回类型 | 示例 |
|------|------|----------|------|
| `get` | 获取单个对象 | `XxxResponse` | `getById(Long id)` |
| `list` | 获取多个对象 | `List<XxxResponse>` | `list(XxxQueryRequest request)` |
| `listPage` | 分页查询 | `Page<XxxResponse>` | `listPage(XxxQueryRequest request, Pageable pageable)` |
| `count` | 统计数量 | `long` | `count(XxxQueryRequest request)` |
| `save` | 新增 | `XxxResponse` | `save(CreateXxxRequest request)` |
| `update` | 修改 | `XxxResponse` | `update(Long id, UpdateXxxRequest request)` |
| `remove` | 删除 | `void` | `removeById(Long id)` |

#### 3.2.2 DTO 命名模式 [MUST]

| 用途 | 命名模式 | 示例 |
|------|----------|------|
| 创建请求 | `Create{Entity}Request` | `CreateUserRequest` |
| 更新请求 | `Update{Entity}Request` | `UpdateUserRequest` |
| 查询请求 | `{Entity}QueryRequest` | `UserQueryRequest` |
| 响应 | `{Entity}Response` | `UserResponse` |

#### 3.2.3 通用命名规则 [MUST]

- 类名 MUST 使用 UpperCamelCase，方法名 MUST 使用 lowerCamelCase，常量 MUST 使用 UPPER_SNAKE_CASE。
- 标识符 MUST 只使用 ASCII 字母、数字；MUST NOT 使用特殊前缀或后缀（如 `m_`、`s_`、`k`）。
- 布尔类型字段 MUST NOT 以 `is` 开头（数据库映射实体除外）。正例：`enabled`、`active`；反例：`isEnabled`、`isActive`。

#### 3.2.4 URI 命名 [MUST]（Controller 层）

- URI 路径 MUST 全部小写，路径段用连字符（`-`）分隔。
- 查询参数名 **[SHOULD]** 使用 camelCase。
- 正例：`/api/v1/user-accounts`；反例：`/api/v1/UserAccounts`、`/api/v1/user_accounts`。

### 3.3 注释与文档

**3.3.1 [MUST]** 每个 Service 方法 MUST 有 Javadoc，包含：用途、`@param`（每个参数）、`@return`（返回值说明，`void` 可省略）、`@throws`（可能抛出的异常）、副作用说明（如有）。

**3.3.2 [MUST]** 每个 DTO 字段 MUST 有 `@Schema(description = "...")`。

**3.3.3 [MUST]** 每个 DTO 类 MUST 有 `@Schema(name = "...", description = "...")`。

**3.3.4 [SHOULD]** `@Schema` 的 `description` SHOULD 使用中文，`name` MUST 使用英文类名。`example` 属性 **[SHOULD]** 提供。

正例：

```java
@Schema(name = "CreateUserRequest", description = "创建用户请求")
public class CreateUserRequest {

    @Schema(description = "用户名，4-32位字母数字下划线", example = "zhangsan")
    @NotBlank
    @Size(min = 4, max = 32)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$")
    private String username;

    @Schema(description = "邮箱地址", example = "zhangsan@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "密码，8-64位，须含大小写字母和数字", example = "P@ssw0rd")
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
```

### 3.4 方法签名标准

**3.4.1** 标准方法签名模板：

```java
public interface XxxService {

    XxxResponse getById(Long id);
    XxxResponse getByCode(String code);

    List<XxxResponse> list(XxxQueryRequest request);

    Page<XxxResponse> listPage(XxxQueryRequest request, Pageable pageable);
    // 或 MyBatis-Plus 方案：
    // IPage<XxxResponse> listPage(XxxQueryRequest request, IPage<XxxResponse> page);

    long count(XxxQueryRequest request);

    XxxResponse save(CreateXxxRequest request);

    XxxResponse update(Long id, UpdateXxxRequest request);

    void updateStatus(Long id, Integer status);

    void removeById(Long id);
    void removeByIds(List<Long> ids);
}
```

**3.4.2 [MUST]** `update` 方法的请求 DTO MUST 包含 `version` 字段用于乐观锁校验。

**3.4.3 [MUST]** `removeByIds` 的 `ids` 参数 MUST 做非空校验和数量上限校验（建议上限 100）。

**3.4.4 [SHOULD]** `getByXxx` 仅在存在唯一业务键（如 code、uuid）时定义；不得为可空字段定义 `getByXxx`。

### 3.5 分页策略

**3.5.1 [MUST]** 项目 MUST 在以下两种分页方案中选择一种，禁止混用：

| 方案 | 参数类型 | 返回类型 | 适用场景 |
|------|----------|----------|----------|
| Spring Data | `Pageable` | `Page<XxxResponse>` | 使用 Spring Data JPA / Spring Data Commons |
| MyBatis-Plus | `IPage<XxxResponse>` | `IPage<XxxResponse>` | 使用 MyBatis-Plus 分页插件 |

**3.5.2 [MUST]** 分页参数中的排序字段 MUST 使用白名单校验，禁止客户端传入任意字段名作为排序条件。

**3.5.3 [SHOULD]** 对于数据频繁变更的场景，SHOULD 优先考虑游标分页而非偏移量分页。

**3.5.4 [SHOULD]** 分页查询 MUST 有默认页大小和最大页大小限制。

**3.5.5** 正例：

```java
// ✅ 正确：使用 Spring Data Pageable + Page
Page<UserResponse> listPage(UserQueryRequest request, Pageable pageable);
```

反例：

```java
// ❌ 错误：混用 Spring Data 和 MyBatis-Plus 分页
Page<UserResponse> listPage(UserQueryRequest request, IPage<User> page);
```

### 3.6 安全与脱敏

**3.6.1 [MUST]** Response DTO MUST NOT 包含以下内部字段：`passwordHash`、`isDeleted`、`activeUsername`、`activeEmail`、`activePhoneNumber`。

**3.6.2 [MUST]** 手机号、邮箱等敏感字段如需返回，MUST 按项目脱敏规范处理，如 `138****1234`。

**3.6.3 [MUST]** `version` 字段处理规则：
- `version` MUST 在 Response DTO 中返回，用于客户端提交更新时回传乐观锁版本号。
- 如 Response 不返回 `version`，则 MUST 提供独立的版本号查询接口或在 HTTP ETag 中携带。

**3.6.4 [SHOULD]** Response DTO 的字段级脱敏 SHOULD 通过 Jackson 序列化注解或独立 VO 层实现，避免在 Service 层写脱敏逻辑。

### 3.7 实体通用规则

**3.7.1 逻辑删除 [MUST]** 由 `@TableLogic` 处理，`remove` 方法执行逻辑删除。

**3.7.2 乐观锁 [MUST]** `update` 方法 MUST 校验 `version`。请求 DTO MUST 包含 `version` 字段。校验失败时 MUST 抛出业务异常并返回明确错误码。

**3.7.3 多租户 [MUST]** 查询和写入 MUST 自动附加 `tenantId` 过滤。`tenantId` MUST NOT 由客户端传入，MUST 由框架拦截器从上下文自动获取。

**3.7.4 审计字段 [MUST]** `createBy`、`createTime`、`updateBy`、`updateTime` MUST 由框架自动维护，MUST NOT 由业务代码手动设置。

**3.7.5 唯一约束 [MUST]** 唯一性校验 MUST 在服务层进行，并给出明确错误码。并发场景下 MUST 由数据库唯一索引兜底，服务层捕获 `DuplicateKeyException` 并转换为业务异常。

### 3.8 DTO 校验规范 [MUST]

**3.8.1** 所有 Request DTO 的字段 MUST 使用 Jakarta Bean Validation 3.0 注解进行校验：

| 字段类型 | 推荐注解 |
|----------|----------|
| 非空字符串 | `@NotBlank` |
| 非空对象 | `@NotNull` |
| 字符串长度 | `@Size(min, max)` |
| 邮箱 | `@Email` |
| 手机号 | `@Pattern(regexp = "^1[3-9]\\d{9}$")` |
| 正整数 | `@Positive` |
| 范围 | `@Min` / `@Max` |

**3.8.2** Controller 层的 Request 参数 MUST 标注 `@Valid` 或 `@Validated`。

**3.8.3** `Update` 请求的 `version` 字段 MUST 标注 `@NotNull`。

### 3.9 事务边界 [MUST]

**3.9.1** `save`、`update`、`removeById`、`removeByIds` MUST 标注 `@Transactional(rollbackFor = Exception.class)`。

**3.9.2** 只读查询方法 SHOULD 标注 `@Transactional(readOnly = true)`。

**3.9.3** 事务注解 MUST 加在 ServiceImpl 的方法上，不得加在 Service 接口上。

### 3.10 错误处理 [MUST]

**3.10.1** Service 层 MUST 抛出自定义业务异常，携带明确错误码。

**3.10.2** Controller 层 MUST 通过 `@RestControllerAdvice` 统一处理异常，返回 RFC 9457 Problem Details 格式：

```json
{
  "type": "https://api.example.com/errors/unique-constraint",
  "title": "唯一约束冲突",
  "status": 409,
  "detail": "用户名 'zhangsan' 已存在",
  "instance": "/api/v1/users"
}
```

**3.10.3 [SHOULD]** 错误码体系 SHOULD 采用分段编码，如 `1xxx` 参数错误，`2xxx` 业务错误，`3xxx` 系统错误。

### 3.11 DTO ↔ Entity 映射 [SHOULD]

**3.11.1** DTO 与 Entity 的转换 SHOULD 使用 MapStruct 或等价工具，禁止在 Service 接口中暴露 Entity。

**3.11.2** 转换逻辑 SHOULD 集中在 `converter` 包或 `assembler` 类中，不与业务逻辑混杂。

## 4. 输出格式（AI 生成时）

生成结果 MUST 按以下顺序输出：

1. Service 接口代码。
2. 请求 DTO 代码。
3. 响应 DTO 代码。
4. 关键设计说明与假设。
5. 如与项目现有规范冲突，以项目现有规范为准，并指出冲突点。

## 5. 检查清单

### 5.1 Service 接口检查清单

- [ ] Service 接口只依赖 DTO，不依赖 Entity
- [ ] 所有方法使用统一前缀（`get` / `list` / `listPage` / `count` / `save` / `update` / `remove`）
- [ ] 每个方法有完整 Javadoc（用途、`@param`、`@return`、`@throws`）
- [ ] 分页方案已确定，未混用 Spring Data 和 MyBatis-Plus
- [ ] `update` 方法签名包含 `version` 参数或请求 DTO 包含 `version`
- [ ] `removeByIds` 对 `ids` 做了非空和数量上限校验

### 5.2 DTO 检查清单

- [ ] 类上有 `@Schema(name, description)`
- [ ] 每个字段有 `@Schema(description)`
- [ ] Request DTO 字段有 Jakarta Bean Validation 注解
- [ ] `Update` 请求包含 `@NotNull` 的 `version` 字段
- [ ] Response DTO 不包含 `passwordHash`、`isDeleted` 等内部字段
- [ ] Response DTO 中的敏感字段（手机号、邮箱）已脱敏
- [ ] Response DTO 包含 `version` 字段（用于乐观锁回传）
- [ ] 布尔字段不以 `is` 开头

### 5.3 架构合规检查清单

- [ ] Entity 不出现在 Service 接口签名中
- [ ] 分页方案全项目统一
- [ ] 多租户 `tenantId` 由框架自动处理，不由客户端传入
- [ ] 审计字段由框架自动维护
- [ ] 逻辑删除与唯一索引冲突已有明确策略
- [ ] 事务注解加在 ServiceImpl 上
- [ ] 排序字段有白名单校验

## 6. 决策记录

### DR-001：Response 中 `version` 字段的暴露

| 项目 | 内容 |
|------|------|
| 决策 | Response DTO 中返回 `version` 字段 |
| 理由 | 乐观锁的 `update` 请求需要客户端回传 `version`，如果 Response 不返回，客户端无法获取版本号 |
| 替代方案 | 提供独立的版本号查询接口或使用 HTTP ETag |
| 影响范围 | 所有 Response DTO |
| 依据 | RFC 7232、Microsoft REST API Guidelines |

### DR-002：逻辑删除与唯一索引冲突

| 项目 | 内容 |
|------|------|
| 决策 | 采用“删除标记 + 唯一索引组合”策略 |
| 理由 | 逻辑删除后原唯一键仍占用，可能导致无法重新创建同名数据 |
| 方案 | 唯一索引改为 `(unique_field, deleted_flag)` 组合索引；删除时将 `deleted_flag` 设为删除时间戳或 UUID |
| 替代方案 | 定期归档已删除数据后物理删除 |
| 影响范围 | 所有有唯一约束且启用逻辑删除的表 |
| 依据 | 项目既有约定、ISO/IEC 11179-5 |

### DR-003：分页方案选型

| 项目 | 内容 |
|------|------|
| 决策 | 项目统一使用 MyBatis-Plus `IPage` / `Page` 分页，或统一使用 Spring Data `Pageable` / `Page` |
| 理由 | 原规范同时列出 Spring Data 和 MyBatis-Plus，存在混用风险 |
| 影响范围 | 所有 Service 接口 |
| 依据 | MyBatis-Plus 3.x 官方文档、Spring Data Commons Reference |

### DR-004：多租户 `tenantId` 注入方式

| 项目 | 内容 |
|------|------|
| 决策 | `tenantId` 由 MyBatis-Plus 租户拦截器从安全上下文自动注入 |
| 理由 | 避免客户端篡改租户 ID，避免业务代码手动拼接租户条件 |
| 影响范围 | 所有涉及多租户的查询和写入 |
| 依据 | MyBatis-Plus 官方文档、OWASP API Security Top 10 |

### DR-005：排序字段安全

| 项目 | 内容 |
|------|------|
| 决策 | 分页排序字段 MUST 使用白名单校验 |
| 理由 | 防止客户端传入任意字段名导致 SQL 注入或性能问题 |
| 影响范围 | 所有分页查询 |
| 依据 | OWASP API Security Top 10: API4:2023 |

### DR-006：错误响应格式

| 项目 | 内容 |
|------|------|
| 决策 | 统一使用 RFC 9457 Problem Details 格式 |
| 理由 | 标准化错误响应，便于客户端统一处理 |
| 影响范围 | 所有 API 错误响应 |
| 依据 | IETF RFC 9457、Microsoft REST API Guidelines |

## 7. 引用来源

1. ISO/IEC/IEEE 42010:2022 — Software, systems and enterprise — Architecture description
2. ISO/IEC 11179-5 — 命名与标识原则
3. IETF RFC 3986 — URI 通用语法
4. IETF RFC 7231 — HTTP/1.1 语义与内容
5. IETF RFC 7232 — HTTP/1.1 条件请求
6. IETF RFC 9457 — Problem Details for HTTP APIs
7. OpenAPI Specification 3.1
8. Google Java Style Guide
9. Oracle Java Code Conventions
10. Jakarta Bean Validation 3.0
11. Spring Framework 6.x Reference
12. MyBatis-Plus 3.x Official Docs
13. Google API Design Guide
14. Microsoft REST API Guidelines
15. Amazon AWS API Design Guidelines
16. Netflix API Design
17. Airbnb Engineering Standards
18. OWASP API Security Top 10 (2023)
19. Alibaba Java Coding Guidelines


# Java ServiceImpl 规范 v2.0

Java Spring Boot service impl 规范应该涵盖哪些内容：

1.类级别注解（@Service、@RequiredArgsConstructor、@Slf4j、@Transactional）

2.依赖注入（构造器注入 vs 字段注入）

3.方法实现模式

4.事务管理

5.异常处理

6.日志记录

7.校验

8.缓存

9.与 converters/mappers 的交互

10.数据访问模式

11.幂等性

12.并发处理


# AI 代码生成提示词

你是一名资深 Java 架构师。请严格按以下规范生成一套完整的 CRUD 代码，包括 Service 接口、ServiceImpl 实现类、请求 DTO、响应 DTO。

---

# Java CRUD 代码生成规范 v2.0

## 0. 使用说明

- 本规范用于生成 Spring Boot + MyBatis-Plus 项目中的 CRUD 代码。
- 生成范围：Service 接口、ServiceImpl 实现类、请求 DTO、响应 DTO。
- 使用方式：将 `{Entity}` 替换为目标实体名（如 `User`），按本规范生成代码。
- 如与项目现有规范冲突，以项目现有规范为准，并在“冲突点说明”中标注。
- 冲突裁决优先级：强制性标准/法规 > 国际/行业标准 > 语言/平台官方规范 > 领先企业最佳实践 > 项目既有约定 > 团队共识。

## 1. 技术栈

| 组件 | 版本/方案 | 备注 |
|------|----------|------|
| Spring Boot | 2.7+ / 3.x / 4.x | 2.x 用 `javax.validation`；3.x/4.x 用 `jakarta.validation` |
| MyBatis-Plus | 3.5.x+ | 分页、乐观锁、逻辑删除、多租户 |
| Lombok | 最新稳定版 | `@RequiredArgsConstructor`、`@Slf4j` |
| Swagger/OpenAPI | 3.x | `@Schema` |
| Bean Validation | 2.0 / 3.0 / 3.1 | 随 Spring Boot 版本 |
| 分页方案 | Spring Data 或 MyBatis-Plus | 二选一，禁止混用 |

## 2. 术语表

| 中文术语 | 英文术语 | 缩写 | 定义 |
|----------|----------|------|------|
| 数据传输对象 | Data Transfer Object | DTO | Service 层入参和出参对象 |
| 值对象 | Value Object | VO | Controller 层对外输出时脱敏或裁剪的对象 |
| 实体 | Entity | — | 与数据库表一一对应的持久化对象 |
| 应用层端口 | Application Port | — | Service 接口，定义应用层对外契约 |
| 乐观锁 | Optimistic Locking | — | 通过 version 检测并发修改冲突 |
| 逻辑删除 | Logical Delete | — | 通过标记字段表示删除，不物理删除 |
| 多租户 | Multi-Tenancy | — | 数据按 tenantId 隔离 |
| 脱敏 | Data Masking | — | 对敏感字段进行部分隐藏处理 |

## 3. 分层架构

**3.1 [MUST]** 依赖方向单一，禁止反向依赖：

```
Controller → Service（接口） → ServiceImpl → Mapper → Entity
```

**3.2 [MUST]** Entity 仅存在于持久化适配器内部，不得出现在 Service 接口签名、Controller 方法签名或任何对外暴露的类中。

**3.3 [MUST]** Service 接口只依赖 DTO（Request / Response），不依赖 Entity。

**3.4 [MAY]** VO 在 Controller 层需要脱敏或裁剪字段时使用，不得传递到 Service 层。

**3.5** 正例：

```java
// ✅ 正确
public interface UserService {
    UserResponse getById(Long id);
    Page<UserResponse> listPage(UserQueryRequest request, Pageable pageable);
}
```

反例：

```java
// ❌ 错误：Service 接口暴露 Entity
public interface UserService {
    User getById(Long id);
}
```

## 4. 命名规范

### 4.1 Service 方法前缀 [MUST]

| 前缀 | 语义 | 返回类型 | 示例 |
|------|------|----------|------|
| `get` | 获取单个对象 | `XxxResponse` | `getById(Long id)` |
| `list` | 获取多个对象 | `List<XxxResponse>` | `list(XxxQueryRequest request)` |
| `listPage` | 分页查询 | `Page<XxxResponse>` | `listPage(XxxQueryRequest request, Pageable pageable)` |
| `count` | 统计数量 | `long` | `count(XxxQueryRequest request)` |
| `save` | 新增 | `XxxResponse` | `save(CreateXxxRequest request)` |
| `update` | 修改 | `XxxResponse` | `update(Long id, UpdateXxxRequest request)` |
| `remove` | 删除 | `void` | `removeById(Long id)` |

### 4.2 DTO 命名模式 [MUST]

| 用途 | 命名模式 | 示例 |
|------|----------|------|
| 创建请求 | `Create{Entity}Request` | `CreateUserRequest` |
| 更新请求 | `Update{Entity}Request` | `UpdateUserRequest` |
| 查询请求 | `{Entity}QueryRequest` | `UserQueryRequest` |
| 响应 | `{Entity}Response` | `UserResponse` |

### 4.3 通用命名规则 [MUST]

- 类名 UpperCamelCase，方法名 lowerCamelCase，常量 UPPER_SNAKE_CASE。
- 标识符只使用 ASCII 字母、数字；不使用特殊前缀或后缀（如 `m_`、`s_`、`k`）。
- 布尔字段不以 `is` 开头（Entity 例外）。正例：`enabled`；反例：`isEnabled`。

### 4.4 URI 命名 [MUST]（Controller 层）

- URI 路径全部小写，路径段用连字符（`-`）分隔。
- 查询参数名使用 camelCase。
- 正例：`/api/v1/user-accounts`；反例：`/api/v1/UserAccounts`。

## 5. Service 接口规范

### 5.1 方法签名模板 [MUST]

```java
public interface XxxService {

    XxxResponse getById(Long id);
    XxxResponse getByCode(String code);

    List<XxxResponse> list(XxxQueryRequest request);

    Page<XxxResponse> listPage(XxxQueryRequest request, Pageable pageable);
    // 或 MyBatis-Plus：
    // IPage<XxxResponse> listPage(XxxQueryRequest request, IPage<XxxResponse> page);

    long count(XxxQueryRequest request);

    XxxResponse save(CreateXxxRequest request);

    XxxResponse update(Long id, UpdateXxxRequest request);

    void updateStatus(Long id, Integer status);

    void removeById(Long id);
    void removeByIds(List<Long> ids);
}
```

### 5.2 接口约束 [MUST]

- 每个方法必须有 Javadoc：用途、`@param`、`@return`、`@throws`、副作用。
- 只依赖 DTO，不出现 Entity。
- `update` 请求 DTO 必须包含 `version` 字段（乐观锁）。
- `removeByIds` 参数必须做非空 + 数量上限校验（建议 100）。
- `getByXxx` 仅在存在唯一业务键（如 code、uuid）时定义。

### 5.3 正例

```java
/**
 * 用户服务接口。
 */
public interface UserService {

    /**
     * 根据 ID 查询用户。
     *
     * @param id 用户 ID，非空
     * @return 用户响应，不存在时抛出 BusinessException
     * @throws BusinessException 用户不存在时抛出
     */
    UserResponse getById(Long id);

    /**
     * 分页查询用户列表。
     *
     * @param request  查询条件
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<UserResponse> listPage(UserQueryRequest request, Pageable pageable);
}
```

## 6. ServiceImpl 规范

### 6.1 类结构与注解 [MUST]

- 类注解：`@Service`、`@RequiredArgsConstructor`、`@Slf4j`、`@Validated`。
- 依赖注入：全部 `private final` + 构造器注入，禁止 `@Autowired` 字段注入。
- 类位于 `service.impl` 包，命名为 `{Entity}ServiceImpl`。

正例：

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserConverter userConverter;
    private final PasswordEncoder passwordEncoder;
}
```

反例：

```java
// ❌ 错误：字段注入
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
}
```

### 6.2 方法实现结构 [MUST]

每个方法遵循：**参数校验 → 业务校验 → 数据操作 → 转换返回**。

```java
@Override
@Transactional(rollbackFor = Exception.class, timeout = 10)
public UserResponse save(CreateUserRequest request) {
        // 1. 业务校验
        if (userMapper.existsByUsername(request.getUsername())) {
        throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 2. 转换 + 加密
        User user = userConverter.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        // 3. 持久化
        userMapper.insert(user);
        // 4. 返回
        return userConverter.toResponse(user);
        }
```

### 6.3 事务管理 [MUST]

- 写方法加 `@Transactional(rollbackFor = Exception.class)`。
- 只读方法加 `@Transactional(readOnly = true)`。
- 事务注解加在方法上，不加在类上。
- 事务内禁止远程调用、消息发送、文件 IO。
- 事务超时应显式指定（如 `timeout = 10`）。

正例：

```java
@Transactional(rollbackFor = Exception.class, timeout = 10)
public UserResponse update(Long id, UpdateUserRequest request) { ... }
```

反例：

```java
// ❌ 错误：事务内做远程调用
@Transactional(rollbackFor = Exception.class)
public void saveOrder(CreateOrderRequest request) {
        orderMapper.insert(order);
        smsClient.send(order.getPhone());   // 禁止
        }
```

### 6.4 异常处理 [MUST]

- 抛出自定义 `BusinessException`，携带明确错误码。
- 系统异常转换为业务异常或统一运行时异常，记录日志。
- `DuplicateKeyException` 捕获并转换为“唯一约束冲突”业务异常。
- 异常信息不含敏感字段（密码、手机号、邮箱）。

正例：

```java
try {
        userMapper.insert(user);
        } catch (DuplicateKeyException e) {
        log.warn("创建用户唯一约束冲突, username={}", request.getUsername(), e);
        throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
```

### 6.5 日志规范 [MUST]

- 使用 SLF4J，禁止 `System.out.println` 和 `e.printStackTrace()`。
- 日志使用占位符 `{}`，禁止字符串拼接。
- 级别使用：ERROR = 系统异常；WARN = 业务异常；INFO = 关键写操作；DEBUG = 调试。
- 禁止输出敏感信息（密码、手机号、邮箱、token）。
- 异常日志保留堆栈：`log.error(msg, e)`。

正例：

```java
log.info("创建用户成功, userId={}, username={}", user.getId(), user.getUsername());
        log.error("查询用户详情失败, userId={}", userId, e);
```

反例：

```java
log.info("创建用户成功, userId=" + user.getId());   // 字符串拼接
        log.info("用户登录, password={}", password);        // 敏感信息
        log.error("查询失败: " + e.getMessage());           // 丢失堆栈
```

### 6.6 数据访问 [MUST]

- 批量查询用 `selectBatchIds` / `selectList`，禁止循环中单查（N+1）。
- 更新用 `updateById` 配合乐观锁 `@Version`，返回 0 表示冲突。
- 删除用逻辑删除（`@TableLogic`）。
- 批量删除校验非空 + 数量上限，空列表直接返回。
- 多租户、逻辑删除、审计字段由框架处理，禁止手动拼接。

正例：

```java
@Override
public void removeByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
        return;
        }
        if (ids.size() > 100) {
        throw new BusinessException(ErrorCode.BATCH_SIZE_EXCEEDED);
        }
        userMapper.deleteBatchIds(ids);
        }
```

反例：

```java
// ❌ 错误：循环单查（N+1）
for (Long id : ids) {
        users.add(userMapper.selectById(id));
        }
```

### 6.7 参数校验 [MUST]

- 需要方法级校验的类加 `@Validated`。
- Spring Boot 3.x+ 启动类加 `@EnableMethodValidation`。
- 约束注解放在**实现类方法参数上**，不放在接口上。
- 区分格式校验（注解）和业务规则校验（代码）。

正例：

```java
@Service
@Validated
public class UserServiceImpl implements UserService {

    @Override
    public UserResponse getById(@NotNull Long id) { ... }

    @Override
    public UserResponse update(@NotNull Long id, @Valid @NotNull UpdateUserRequest request) { ... }
}
```

### 6.8 缓存使用 [SHOULD]

- 频繁读取、低频变更的数据使用缓存。
- 缓存 key 包含租户 ID，避免多租户串读。
- 更新/删除时使缓存失效（`@CacheEvict`）。
- 缓存设置合理 TTL。
- 空结果不缓存或短 TTL 缓存，避免缓存穿透。

正例：

```java
@Cacheable(cacheNames = "user:detail", key = "#tenantId + ':' + #id")
public UserResponse getById(Long id) { ... }

@CacheEvict(cacheNames = "user:detail", key = "#tenantId + ':' + #id")
public UserResponse update(Long id, UpdateUserRequest request) { ... }
```

### 6.9 幂等性 [MUST]

- 创建、支付、扣减库存等写操作支持幂等。
- 通过唯一请求号（requestId）或业务唯一键实现。
- 使用 Redis `SETNX` 或数据库唯一索引，禁止内存缓存（多实例失效）。
- 幂等失败返回明确错误码或原结果，不重复执行。

正例：

```java
String idempotentKey = "order:create:" + request.getRequestId();
        boolean first = redisTemplate.opsForValue()
        .setIfAbsent(idempotentKey, "1", Duration.ofMinutes(10));
        if (!first) {
        return orderConverter.toResponse(orderMapper.selectByRequestId(request.getRequestId()));
        }
```

### 6.10 转换规范 [MUST]

- DTO ↔ Entity 通过 Converter / Assembler 完成。
- 禁止在 ServiceImpl 手动逐字段赋值（字段极少且无复用时除外）。
- 转换器使用 MapStruct，配置 `unmappedTargetPolicy = ReportingPolicy.ERROR`。

正例：

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserConverter {
    UserResponse toResponse(User user);
    User toEntity(CreateUserRequest request);
    void updateEntity(@MappingTarget User user, UpdateUserRequest request);
}
```

## 7. DTO 规范

### 7.1 类注解 [MUST]

- 类上必须有 `@Schema(name = "...", description = "...")`。
- 每个字段必须有 `@Schema(description = "...")`，建议提供 `example`。
- `description` 用中文，`name` 用英文类名。

### 7.2 请求 DTO 校验 [MUST]

所有 Request DTO 字段必须使用 Bean Validation 注解：

| 字段类型 | 推荐注解 |
|----------|----------|
| 非空字符串 | `@NotBlank` |
| 非空对象 | `@NotNull` |
| 字符串长度 | `@Size(min, max)` |
| 邮箱 | `@Email` |
| 手机号 | `@Pattern(regexp = "^1[3-9]\\d{9}$")` |
| 正整数 | `@Positive` |
| 范围 | `@Min` / `@Max` |

`Update` 请求的 `version` 字段必须加 `@NotNull`。

正例：

```java
@Schema(name = "CreateUserRequest", description = "创建用户请求")
public class CreateUserRequest {

    @Schema(description = "用户名，4-32位字母数字下划线", example = "zhangsan")
    @NotBlank
    @Size(min = 4, max = 32)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$")
    private String username;

    @Schema(description = "邮箱地址", example = "zhangsan@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "密码，8-64位，须含大小写字母和数字", example = "P@ssw0rd")
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
```

### 7.3 响应 DTO 安全 [MUST]

- Response DTO 不包含：`passwordHash`、`isDeleted`、`activeUsername`、`activeEmail`、`activePhoneNumber`。
- Response DTO 必须包含 `version` 字段（用于乐观锁回传）。
- 手机号、邮箱等敏感字段按项目脱敏规范处理（如 `138****1234`）。
- 字段级脱敏通过 Jackson 序列化注解或 VO 层实现，不在 Service 层写脱敏逻辑。

正例：

```java
@Schema(name = "UserResponse", description = "用户响应")
public class UserResponse {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @Schema(description = "邮箱（脱敏）", example = "zh****@example.com")
    private String email;

    @Schema(description = "版本号，用于乐观锁", example = "1")
    private Integer version;
}
```

## 8. 实体规则（ServiceImpl 内部遵守）

| 规则 | 实现方式 | 说明 |
|------|----------|------|
| 逻辑删除 [MUST] | `@TableLogic` | remove 执行逻辑删除 |
| 乐观锁 [MUST] | `@Version` | update 校验 version，冲突抛业务异常 |
| 多租户 [MUST] | MyBatis-Plus 租户拦截器 | 自动附加 tenantId，不由客户端传入 |
| 审计字段 [MUST] | `MetaObjectHandler` | createBy/createTime/updateBy/updateTime 自动维护 |
| 唯一约束 [MUST] | 服务层校验 + 数据库唯一索引兜底 | 并发时捕获 `DuplicateKeyException` |

## 9. 分页策略

**9.1 [MUST]** 分页方案二选一，禁止混用：

| 方案 | 参数类型 | 返回类型 |
|------|----------|----------|
| Spring Data | `Pageable` | `Page<XxxResponse>` |
| MyBatis-Plus | `IPage<XxxResponse>` | `IPage<XxxResponse>` |

**9.2 [MUST]** 分页排序字段必须白名单校验，禁止客户端传入任意字段名。

**9.3 [SHOULD]** 数据频繁变更的场景优先使用游标分页。

**9.4 [SHOULD]** 分页查询必须有默认页大小和最大页大小限制。

正例：

```java
Page<UserResponse> listPage(UserQueryRequest request, Pageable pageable);
```

反例：

```java
// ❌ 错误：混用
Page<UserResponse> listPage(UserQueryRequest request, IPage<User> page);
```

## 10. 错误处理

**10.1 [MUST]** Service 层抛出自定义业务异常，携带明确错误码。

**10.2 [MUST]** Controller 层通过 `@RestControllerAdvice` 统一处理异常，推荐 RFC 9457 Problem Details 格式：

```json
{
  "type": "https://api.example.com/errors/unique-constraint",
  "title": "唯一约束冲突",
  "status": 409,
  "detail": "用户名 'zhangsan' 已存在",
  "instance": "/api/v1/users"
}
```

**10.3 [SHOULD]** 错误码体系采用分段编码，如 `1xxx` 参数错误，`2xxx` 业务错误，`3xxx` 系统错误。

## 11. 输出格式

生成结果必须按以下顺序输出：

1. Service 接口代码
2. ServiceImpl 实现类代码
3. 请求 DTO 代码（Create、Update、Query）
4. 响应 DTO 代码
5. 关键设计说明与假设
6. 如与项目现有规范冲突，以项目现有规范为准，并指出冲突点

## 12. 检查清单

### 12.1 Service 接口

- [ ] 只依赖 DTO，不依赖 Entity
- [ ] 方法前缀统一（get / list / listPage / count / save / update / remove）
- [ ] 每个方法有完整 Javadoc
- [ ] 分页方案已确定，未混用
- [ ] `update` 请求 DTO 包含 `version`
- [ ] `removeByIds` 做非空 + 数量上限校验

### 12.2 ServiceImpl

- [ ] 类注解齐全（`@Service`、`@RequiredArgsConstructor`、`@Slf4j`、`@Validated`）
- [ ] 构造器注入，无 `@Autowired` 字段注入
- [ ] 写方法有 `@Transactional(rollbackFor = Exception.class)`
- [ ] 只读方法有 `@Transactional(readOnly = true)`
- [ ] 事务内无远程调用、消息发送、文件 IO
- [ ] 业务异常携带错误码
- [ ] `DuplicateKeyException` 被捕获并转换
- [ ] 日志使用占位符，无敏感信息
- [ ] 批量查询无 N+1
- [ ] 更新使用乐观锁
- [ ] 删除为逻辑删除
- [ ] 缓存 key 包含租户 ID
- [ ] 写操作支持幂等
- [ ] 转换使用 Converter，无手动逐字段赋值

### 12.3 DTO

- [ ] 类上有 `@Schema(name, description)`
- [ ] 每个字段有 `@Schema(description)`
- [ ] Request 字段有 Bean Validation 注解
- [ ] `Update` 请求包含 `@NotNull` 的 `version`
- [ ] Response 不包含 `passwordHash`、`isDeleted` 等内部字段
- [ ] Response 中敏感字段已脱敏
- [ ] Response 包含 `version` 字段
- [ ] 布尔字段不以 `is` 开头

### 12.4 架构合规

- [ ] Entity 不出现在 Service 接口签名中
- [ ] 分页方案全项目统一
- [ ] 多租户 `tenantId` 由框架自动处理
- [ ] 审计字段由框架自动维护
- [ ] 逻辑删除与唯一索引冲突已有策略
- [ ] 事务注解加在 ServiceImpl 方法上
- [ ] 排序字段有白名单校验

## 13. 决策记录

| 编号 | 决策 | 理由 | 依据 |
|------|------|------|------|
| DR-001 | Response DTO 返回 `version` 字段 | 乐观锁 update 需要客户端回传版本号 | RFC 7232、Microsoft REST API Guidelines |
| DR-002 | 逻辑删除 + 唯一索引组合策略 | 避免删除后唯一键占用导致无法重建 | 项目既有约定、ISO/IEC 11179-5 |
| DR-003 | 分页方案二选一 | 禁止 Spring Data 与 MyBatis-Plus 混用 | MyBatis-Plus 官方文档、Spring Data Commons |
| DR-004 | `tenantId` 由框架拦截器注入 | 避免客户端篡改，避免手动拼接 | MyBatis-Plus 官方文档、OWASP API Security Top 10 |
| DR-005 | 排序字段白名单校验 | 防 SQL 注入和性能问题 | OWASP API Security Top 10: API4:2023 |
| DR-006 | 错误响应 RFC 9457 Problem Details | 标准化错误响应，便于客户端统一处理 | IETF RFC 9457、Microsoft REST API Guidelines |
| DR-007 | ServiceImpl 不继承 MyBatis-Plus `ServiceImpl` | 避免 Entity 暴露到公开方法签名 | Clean Architecture、Hexagonal Architecture |
| DR-008 | 事务注解加在方法上 | 避免只读查询被纳入事务，减少连接占用 | Spring Framework Reference |
| DR-009 | 日志使用 `@Slf4j` | 与 `@RequiredArgsConstructor` 技术栈统一，减少样板代码 | 项目规范、主流实践 |
| DR-010 | `@Validated` 加在 ServiceImpl 类上 | 启用方法级参数校验，覆盖非 HTTP 入口调用 | Spring Framework Reference |

## 14. 引用来源

1. ISO/IEC/IEEE 42010:2022 — Architecture description
2. ISO/IEC 11179-5 — 命名与标识原则
3. IETF RFC 3986 — URI 通用语法
4. IETF RFC 7231 — HTTP/1.1 语义
5. IETF RFC 7232 — HTTP/1.1 条件请求
6. IETF RFC 9457 — Problem Details for HTTP APIs
7. OpenAPI Specification 3.1
8. Google Java Style Guide
9. Oracle Java Code Conventions
10. Jakarta Bean Validation 3.0
11. Spring Framework 6.x Reference
12. MyBatis-Plus 3.x Official Docs
13. Google API Design Guide
14. Microsoft REST API Guidelines
15. Amazon AWS API Design Guidelines
16. Netflix API Design
17. Airbnb Engineering Standards
18. OWASP API Security Top 10 (2023)
19. Alibaba Java Coding Guidelines
20. SLF4J Official Documentation
