/**
 * Realm 框架的位置信息服务自动配置模块（IP 定位 + 城市数据查询）。
 *
 * <p><b>模块定位：</b>
 * 为业务模块提供完全离线的 IP 地址解析与城市字典查询能力。核心基于
 * {@code ip2region.xdb} 离线库与内置的 {@code cities.json} 字典，不依赖外部网络请求。
 * 对外只暴露 {@code LocationFacade} 与只读 DTO，不承载风控、审计等业务逻辑。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>只读查询</b>：本模块只提供查询能力，绝不修改或写入任何业务数据，
 *         也不参与登录审计或风控决策。</li>
 *     <li><b>资源文件免过滤</b>：{@code ip2region.xdb} 为二进制文件，必须在 POM 的
 *         {@code <resources>} 中配置 {@code <filtering>false</filtering>}，
 *         否则 Maven 变量替换会损坏文件导致启动失败。</li>
 *     <li><b>真实 IP 提取安全</b>：{@code ClientIpResolver} 解析 {@code X-Forwarded-For}
 *         等代理头时，必须校验其格式并对不信任的代理链进行安全截断，防范伪造 IP 注入
 *         （依据 OWASP 安全指南）。</li>
 *     <li><b>缓存非必需</b>：{@code CaffeineLocationCache} 仅作为可选增强，未引入
 *         Caffeine 依赖时，模块应正常退化为无缓存模式。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Provides fully offline IP geolocation and city dictionary lookup capabilities
 * for business modules, based on the {@code ip2region.xdb} file and a built-in
 * {@code cities.json} dictionary. Exposes only the {@code LocationFacade} and
 * read-only DTOs; carries no risk-control or audit logic.
 *
 * @author QI Guang
 */
package cn.realm.cloud.framework.biz.location;
