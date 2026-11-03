package cn.realm.cloud.framework.security.core;

/**
 * 权限检查器，供 {@code @PreAuthorize("@ss.hasPermission(...)")} 使用。
 *
 * <p>依据：OWASP ASVS V4 要求访问控制必须在每个请求上强制执行，
 * 不能依赖前端隐藏按钮。实现中同时校验 status==0，停用账号即使持有有效 token 也应被拒绝。
 *
 * <p>注意：本类由 starter 通过 {@code WebSecurityBeanConfiguration} 注册为 "ss" Bean，
 * 业务方可通过自定义同名 Bean 覆盖。
 *
 * @author QI Guang
 */
public class PermissionChecker {

    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }
        return SecurityUtils.findLoginUser()
                .filter(u -> u.status() != null && u.status() == 0)
                .map(u -> u.permissions().contains(permission))
                .orElse(false);
    }

    public boolean hasAnyPermission(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        return SecurityUtils.findLoginUser()
                .filter(u -> u.status() != null && u.status() == 0)
                .map(u -> {
                    for (String p : permissions) {
                        if (u.permissions().contains(p)) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }
}
