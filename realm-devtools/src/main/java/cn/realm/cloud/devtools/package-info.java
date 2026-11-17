/**
 * Realm 框架的研发工具与元数据管理模块。
 *
 * <p><b>模块定位：</b>
 * 包含代码生成器、数据源管理、参数配置三大能力。本模块只负责“管理元数据”——
 * 代码生成器的表结构、数据源的连接信息、参数的键值对。它绝不承载运行时的业务能力，
 * 例如数据源管理的产物是数据库中的配置记录，而不是 Spring 容器中真实的 {@code DataSource} 实例。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>不承载运行时数据源路由</b>：数据源路由由 {@code realm-data-access} 负责。
 *         本模块的 {@code ConnectionTester} 仅在测试连通性时临时创建连接，用完即毁，
 *         绝不注册到 Spring 容器，避免与主数据源产生 Bean 冲突。</li>
 *     <li><b>代码生成器不作 Bean</b>：{@code MyBatisPlusGenerator} 是一个包含 {@code main()}
 *         的开发期工具，禁止标注 {@code @Component} 或 {@code @Service}，禁止被 Spring 扫描。</li>
 *     <li><b>不包含运维能力</b>：定时任务管理、服务器信息等运维功能归 {@code realm-platform}，
 *         本模块只做面向开发者的研发提效工具。</li>
 *     <li><b>密码加密不可逆</b>：数据源密码的存储必须经过不可逆或高强度的对称加密，
 *         {@code CryptoSupport} 负责加密与解密钩子，严禁在日志或接口响应中明文返回密码。</li>
 * </ul>
 *
 * <p><b>Module Positioning:</b>
 * Developer tools and metadata management (code generator, datasource config, parameter config).
 * Manages metadata only; never carries runtime capabilities (e.g., datasource routing or real DataSource instances).
 *
 * @author QI Guang
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "realm-core",                        // 基础契约
                "realm-utils",                       // 工具类（如字符串处理）
                "realm-audit-logging :: api",        // 数据源变更、参数修改的审计
                "realm-web-spring-boot-starter",     // 管理后台的 REST API
                "realm-mybatis-plus-spring-boot-starter" // 持久化元数据表 (infra_*)
                // 注意：绝不依赖 realm-platform，两者互不干涉
        }
)
package cn.realm.cloud.devtools;
