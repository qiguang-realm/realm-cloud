/**
 * Realm 框架的授权决策自动配置模块。
 *
 * <p><b>模块定位：</b>
 * 基于 Spring Security 6+ 的 {@code AuthorizationManager} 体系，为方法级（{@code @PreAuthorize}）
 * 与 URL 级请求提供统一的授权决策入口。将授权信息（角色、权限码）应用于具体的访问请求，
 * 产生允许或拒绝的决策。不包含用户登录、令牌签发（认证），也不包含审计记录写入。
 *
 * <p><b>架构红线（ArchUnit 强制检查）：</b>
 * <ul>
 *     <li><b>只做决策，不做执行（NIST SP 800-53 AC-24）：</b>
 *         本模块负责“决定是否允许访问”，拒绝后的异常抛出、HTTP 403 响应由 Spring Security 处理。</li>
 *     <li><b>不定义权限规则：</b>不硬编码角色或权限码，权限数据来源由 {@code PermissionEvaluator} 或
 *         未来的 {@code TupleReader} 提供。</li>
 *     <li><b>认证与授权分离：</b>严禁在本模块配置表单登录、JWT 解码器等认证过滤器。</li>
 * </ul>
 */
package cn.realm.cloud.framework.authorization;
