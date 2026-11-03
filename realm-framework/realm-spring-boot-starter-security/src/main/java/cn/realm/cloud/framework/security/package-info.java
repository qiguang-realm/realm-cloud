/**
 * Realm 安全基础组件。
 *
 * <p>集中实现认证/授权能力，通过 SPI 与业务模块解耦：
 * <ul>
 *   <li>认证端点：{@code auth} 包，暴露 /auth/login 等标准端点</li>
 *   <li>核心模型与 SPI：{@code core} 包，定义 LoginUser、LoginUserProvider 等</li>
 *   <li>过滤器与异常处理：{@code web} 包</li>
 *   <li>自动配置：{@code config} 包</li>
 *   <li>操作日志：{@code operatelog} 包（bizlog 封装）</li>
 * </ul>
 *
 * <p>设计依据：OWASP ASVS V1 要求安全控制集中化实现。本 starter 由业务模块引入后
 * 获得统一的安全基线，而非各模块各自实现。
 */
package cn.realm.cloud.framework.security;
