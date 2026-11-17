/**
 * Workforce IAM 业务实现模块根包。
 *
 * <p><b>模块职责：</b>
 * 负责内部员工身份与访问管理（Workforce IAM）的业务实现，包括用户、部门、角色、菜单、OAuth 客户端与令牌。
 * 对外暴露的契约（Facade 接口、DTO、领域事件）定义在 {@code realm-workforce-identity-api} 模块中，
 * 本模块通过 {@link cn.realm.cloud.workforce.identity.IdentityFacadeImpl} 来实现这些契约。
 *
 * <p><b>包结构约束（严格隔离）：</b>
 * <ul>
 *     <li>根包下仅保留 {@code IdentityFacadeImpl}（对外契约实现）。</li>
 *     <li>所有具体的业务逻辑（Controller、Application Service、Domain、Repository）必须放置在
 *         {@code internal} 子包下，由 Spring Modulith 在编译期和运行期强制隐藏，禁止被其他业务模块直接引用。</li>
 * </ul>
 *
 * <p><b>Module Responsibilities:</b>
 * Responsible for the business implementation of Workforce Identity and Access Management,
 * including users, departments, roles, menus, OAuth clients, and tokens.
 * The public contracts (Facade interfaces, DTOs, domain events) are defined in the
 * {@code realm-workforce-identity-api} module, and this module implements them via
 * {@link cn.realm.cloud.workforce.identity.IdentityFacadeImpl}.
 *
 * <p><b>Package Structure Constraints:</b>
 * Only {@code IdentityFacadeImpl} is kept in the root package. All specific business logic
 * must be placed under the {@code internal} sub-package, strictly hidden from other business modules.
 *
 * @author QI Guang
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                // 1. 自身契约：必须实现 IdentityFacade 并发布 UserRegisteredEvent 等事件
                "realm-workforce-identity-api",

                // 2. 框架基础：技术契约、通用异常、分页结果
                "realm-core",
                "realm-utils",

                // 3. 横切能力：审计注解、多租户上下文
                "realm-audit-logging :: api",
                "realm-multitenancy :: api",

                // 4. 持久化基础：ORM 环境与数据源
                "realm-mybatis-plus-spring-boot-starter",

                // 5. Web 基础：REST API 的支撑能力
                "realm-web-spring-boot-starter"

                // 架构红线（架构测试会自动拦截以下依赖）：
                // 绝不依赖 realm-authorization：授权模块消费本模块的数据做决策，反向依赖会造成循环。
                // 绝不依赖 realm-customer：Workforce IAM 与 CIAM 是两个独立身份域，不相互耦合。
                // 绝不依赖其他业务模块（payment, platform, devtools 等）。
        }
)
package cn.realm.cloud.workforce.identity;
