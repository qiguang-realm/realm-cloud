📁 realm-core 详细包结构全景

realm-core/
└── src/main/java/cn/realm/cloud/framework/core/
│
├── package-info.java             # [必须] 模块全局定位与架构红线声明
│
├── dto/                          # 跨模块数据传输契约 (Data Transfer Objects)
│   ├── package-info.java
│   ├── PageQuery.java            # 通用分页请求参数（record）
│   ├── PageResult.java           # 通用分页响应结构（不可变类/record）
│   ├── SortQuery.java            # 通用排序参数（支持多字段排序）
│   ├── ApiResponse.java          # 统一响应体（适配 ADR-002 的 api-response 兼容模式）
│   └── BaseIdDto.java            # 基础 ID 数据传输对象（包含 Long id 等通用字段）
│
├── exception/                    # 统一异常体系 (Exceptions)
│   ├── package-info.java
│   ├── ErrorCode.java            # 错误码顶层接口（定义 getCode() 和 getMessage() 规范）
│   ├── BusinessException.java    # 业务异常（携带 ErrorCode，预期内的异常，如参数校验失败）
│   └── SystemException.java      # 系统异常（不可预期异常，如数据库宕机，用于触发告警）
│
├── type/                         # 通用类型与枚举定义 (Types & Enums)
│   ├── package-info.java
│   ├── YesNoEnum.java            # 通用是/否枚举（1-是，0-否）
│   ├── StatusEnum.java           # 通用启用/停用状态枚举（0-正常，1-停用）
│   ├── UserTypeEnum.java         # 用户类型（1-内部员工，2-外部客户）
│   └── DataScopeEnum.java        # 数据权限范围（全部、本部门、仅本人等）
│
└── context/                      # 中立运行时上下文抽象 (Context Abstractions)
├── package-info.java
├── SubjectContext.java       # 当前操作主体上下文接口（获取 userId、username 等）
├── TenantContext.java        # 当前租户上下文接口（获取 tenantId）
└── RequestContext.java       # 当前请求上下文接口（获取 traceId、客户端 IP 等）
