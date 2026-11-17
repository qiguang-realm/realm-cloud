package cn.realm.cloud.framework.core.principal;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 安全主体访问工具。
 *
 * <p><b>定位：</b>本类是<b>访问 Spring Security 安全上下文的统一入口</b>，
 * 只负责“获取当前已认证主体（Principal）”及其权限数据，
 * 不负责“执行授权决策”，也不负责“管理认证状态”。
 *
 * <p><b>命名依据：</b>{@code Principal} 是 Java Security 与 OWASP ASVS 中
 * 表示“可被系统识别的实体”的标准术语。本类围绕 {@link UserPrincipal}
 * 构建，旨在提供对当前请求主体的统一访问能力。
 *
 * <p><b>放置位置与依赖：</b>位于 {@code realm-core} 的 {@code security} 包，
 * 依赖 {@code spring-security-core}（{@code provided} 作用域）。
 * 业务模块（如 {@code realm-workforce-identity-server}）即使不引入授权模块，
 * 也需要通过本类获取当前登录主体，用于填充审计字段（{@code create_by}）
 * 或进行数据归属判断。
 *
 * <p><b>设计依据（国际标准与最佳实践）：</b>
 * <ul>
 *     <li><b>OWASP ASVS V3（会话管理）：</b>身份信息必须从服务端的安全上下文
 *         （{@link SecurityContextHolder}）读取，绝不能信任客户端传入的
 *         {@code userId} 参数。</li>
 *     <li><b>OWASP ASVS V4.1（访问控制）：</b>权限判断必须基于服务端维护的
 *         授权数据（即 {@link Authentication#getAuthorities()}），
 *         不能基于客户端提交的声明。</li>
 *     <li><b>Spring Security 官方规范：</b>对于 Web 层，推荐使用
 *         {@code @AuthenticationPrincipal} 注入；对于 Service 层或非 Web 组件，
 *         通过本类封装读取，避免业务代码直接依赖 {@code SecurityContextHolder} 的静态方法。</li>
 * </ul>
 *
 * @author QI Guang
 * @see UserPrincipal
 * @see org.springframework.security.core.Authentication
 * @see org.springframework.security.core.context.SecurityContextHolder
 */
public final class PrincipalUtils {

    /**
     * 工具类禁止实例化。
     */
    private PrincipalUtils() {
        throw new UnsupportedOperationException("工具类禁止实例化");
    }

    // ================================================================
    // 认证上下文
    // ================================================================

    /**
     * 获取当前请求的 {@link Authentication} 对象。
     *
     * <p><b>返回值语义：</b>
     * <ul>
     *     <li>{@code null}：当前线程没有安全上下文（如异步线程未传递上下文、
     *         Mock 测试未设置 SecurityContext）。</li>
     *     <li>非 {@code null} 但 {@code isAuthenticated() == false}：匿名认证。</li>
     *     <li>非 {@code null} 且 {@code isAuthenticated() == true}：已认证主体。</li>
     * </ul>
     *
     * @return 当前 {@link Authentication}；未设置时返回 {@code null}
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    // ================================================================
    // 安全主体
    // ================================================================

    /**
     * 尝试获取当前已认证的主体（{@link UserPrincipal}）。
     *
     * <p><b>判定逻辑（三层校验）：</b>
     * <ol>
     *     <li>{@link Authentication} 非 {@code null}</li>
     *     <li>{@code isAuthenticated() == true}——过滤掉匿名认证
     *         （匿名主体的 {@code getPrincipal()} 是字符串 {@code "anonymousUser"}）</li>
     *     <li>{@code getPrincipal()} 是 {@link UserPrincipal} 的实例——
     *         防止某些第三方认证方式返回其他类型的 Principal</li>
     * </ol>
     *
     * <p><b>注意：</b>如果认证模块将 {@link UserPrincipal} 包装在
     * 一个自定义的适配器中（例如为了兼容 Spring Security 的 {@code UserDetails}），
     * 需要在业务侧或认证模块侧提供相应的提取逻辑。
     * 这里假设认证成功后，Principal 直接被设置为 {@link UserPrincipal}。
     *
     * @return 当前安全主体；未登录或 Principal 类型不匹配时返回 {@link Optional#empty()}
     */
    public static Optional<UserPrincipal> findUserPrincipal() {
        Authentication auth = getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        if (!(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    /**
     * 获取当前已认证的主体，未登录时抛出异常。
     *
     * <p><b>异常类型的选择：</b>使用 Spring Security 的
     * {@link AuthenticationCredentialsNotFoundException}。
     * 这符合框架的异常体系，可被 Spring Security 的错误处理链或全局异常处理器
     * （如 {@code GlobalExceptionHandler}）捕获，并转换为标准的 HTTP 401 响应。
     *
     * <p><b>异步环境提示：</b>如果在异步线程中调用，需确保
     * {@code SecurityContext} 已通过 {@code DelegatingSecurityContextExecutor}
     * 等方式正确传递。否则抛出异常是正确的行为，不应静默返回空对象。
     *
     * @return 当前安全主体，永不为 {@code null}
     * @throws AuthenticationCredentialsNotFoundException 未登录或 Principal 类型不匹配
     */
    public static UserPrincipal getUserPrincipal() {
        return findUserPrincipal()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "未登录或登录已过期"));
    }

    /**
     * 获取当前登录用户的 ID（便捷方法）。
     *
     * @return 当前登录用户 ID，永不为 {@code null}
     * @throws AuthenticationCredentialsNotFoundException 未登录
     */
    public static Long getUserId() {
        return getUserPrincipal().id();
    }

    // ================================================================
    // 权限判断
    // ================================================================

    /**
     * 判断当前认证主体是否拥有指定权限。
     *
     * <p><b>为什么基于 {@link Authentication#getAuthorities()}：</b>
     * 这是 Spring Security 的标准契约。无论认证方式如何（JWT、Session 等），
     * 认证成功后框架都会将权限填充为该集合。这确保了权限判断的单一可信来源，
     * 避免了与 {@code @PreAuthorize} 等声明式授权产生逻辑分叉。
     *
     * <p><b>重要提醒：</b>此方法<b>仅供非声明式场景</b>的便捷判断使用
     * （如动态菜单渲染、数据范围过滤）。对于 Controller 或 Service 的核心业务
     * 访问控制，应优先使用 {@code @PreAuthorize} 或 {@code AuthorizationManager}，
     * 以确保授权在方法执行前拦截，并统一拒绝响应格式。
     *
     * @param authority 权限标识（如 {@code "system:user:create"}、{@code "ROLE_ADMIN"}）
     * @return {@code true} 表示当前主体拥有该权限；未登录或无此权限时返回 {@code false}
     */
    public static boolean hasAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return false;
        }
        Authentication auth = getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
