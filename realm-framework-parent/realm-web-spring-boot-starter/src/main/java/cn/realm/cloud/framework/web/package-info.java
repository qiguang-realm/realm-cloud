/**
 * realm-web-spring-boot-starter 的模块边界声明。
 *
 * 职责：HTTP 入站横切——RFC 9457 错误映射、W3C Trace Context、日志脱敏、
 *       分页绑定、版本解析、CORS 与安全响应头。
 *
 * 不做：
 *  - 认证授权（realm-workforce-identity / realm-authorization）
 *  - 出站调用（realm-openfeign）
 *  - ORM / 数据访问（realm-data-access）
 *  - 强制 OpenAPI 文档
 *
 * 架构红线：
 *  - autoconfigure/ 只装配，不拥有业务语义；
 *  - 所有 *AutoConfiguration 只通过 AutoConfiguration.imports 加载，
 *    不得被 @ComponentScan 扫描；
 *  - trace/ 的上下文传播必须双向（入站 Extract + 出站 Inject），
 *    下游模块复用 TraceContextHolder，不得各自实现。
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "realm-core",
                "realm-utils"
        }
)
package cn.realm.cloud.framework.web;
