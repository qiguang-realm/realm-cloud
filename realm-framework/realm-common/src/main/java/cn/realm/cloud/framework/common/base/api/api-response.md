# API 统一响应体系

本模块提供一致的 REST API 响应结构、错误码体系、异常处理、链路追踪与自动包装能力。

**模块坐标**

| 项目 | 值 |
|---|---|
| Maven 模块 | `realm-framework / realm-common` |
| Package | `cn.realm.cloud.framework.common.base.api` |
| 维护者 | QI Guang |
| 起始版本 | 1.0.0 |

---

## 一、组件清单

| 组件 | 职责 | 层次 |
|---|---|---|
| `ApiResponse<T>` | **业务数据契约**：统一响应体（code / message / data / timestamp / traceId） | DTO |
| `ErrorCode` | **错误码枚举**：分层管理业务错误码（通用 / 用户 / 订单 / ...） | 枚举 |
| `BusinessException` | **业务异常**：携带 `ErrorCode` 的运行时异常 | 异常 |
| `GlobalExceptionHandler` | **全局异常处理**：`@RestControllerAdvice` 统一捕获并转换为 `ApiResponse` | 切面 |
| `TraceIdFilter` | **链路追踪**：从请求头提取 / 生成 `traceId`，写入 MDC 与响应头 | Servlet Filter |
| `GlobalResponseBodyAdvice` | **自动包装**：`ResponseBodyAdvice` 将 Controller 返回值自动包装为 `ApiResponse` | 切面 |
| `Responses` | **静态工厂**：构建 `ResponseEntity<ApiResponse<T>>`（HTTP 状态 + 业务状态） | 工具类 |
| `ResponseBuilder<T>` | **链式构建器**：为已有 `ApiResponse` 追加 HTTP 状态与响应头 | Builder |

---

## 二、组件关系图


