/**
 * Realm 框架的外部客户身份与会员管理模块（CIAM）。
 *
 * <p><b>模块定位：</b>
 * 负责外部客户（C端用户）的注册、档案管理、等级成长、积分签到与收货地址。
 * 严格遵循 CIAM（Customer Identity and Access Management）与 Workforce IAM
 * （内部员工身份，由 realm-workforce-identity 负责）的隔离原则，两者不共享用户表。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>身份域隔离</b>：绝不依赖 {@code realm-workforce-identity}。如果业务场景需要
 *         "员工也是客户"，必须通过应用层的事件监听或编排来同步，绝不能在代码层面双向耦合。</li>
 *     <li><b>授权模型独立</b>：绝不依赖 {@code realm-authorization}。客户的权限模型是
 *         "仅限本人"（BOLA 防御），不适用员工内部的 RBAC 菜单/按钮权限体系。</li>
 *     <li><b>facade + internal 边界</b>：根包下只保留 {@code CustomerFacade}、{@code CustomerDto}
 *         与 {@code events}。所有复杂的业务逻辑（积分计算、等级跃迁、签到防刷）
 *         必须在 {@code internal} 的对应子域内闭环实现，避免跨模块泄漏。</li>
 *     <li><b>幂等与防刷</b>：积分发放与签到逻辑必须实现严格的幂等控制（依赖
 *         {@code realm-stability} 或应用层唯一索引），防止并发重试导致资产超发。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Manages external customer identity and membership (CIAM). Handles registration,
 * profiles, loyalty (tiers, points, check-in), and addresses. Strictly isolated from
 * Workforce IAM (realm-workforce-identity); the two domains do not share user tables.
 *
 * @author QI Guang
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "realm-core",
                "realm-utils",
                "realm-audit-logging :: api",       // 敏感操作审计（如修改密码、修改手机号）
                "realm-multitenancy :: api",        // 客户按租户隔离
                "realm-messaging :: api",           // 发送注册验证码、积分变动通知
                "realm-stability :: api",           // 签到/积分发放的幂等与分布式锁支撑（可选）
                "realm-biz-location :: api"         // 注册时的 IP 归属地解析（可选）
                // 不列 realm-authorization：客户不走员工 RBAC
                // 不列 realm-workforce-identity：CIAM 与 Workforce IAM 是两个独立身份域
        }
)
package cn.realm.cloud.customer;
