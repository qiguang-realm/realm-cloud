package cn.realm.cloud.framework.common.util.spring;

import cn.hutool.extra.spring.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * Spring 工具类，继承自 Hutool 的 SpringUtil，并提供生产环境判断等增强功能。
 *
 * @author QI Guang
 */
public class SpringUtils extends SpringUtil {

    private static final Logger log = LoggerFactory.getLogger(SpringUtils.class);

    /**
     * 缓存生产环境判断结果（volatile 保证可见性）
     */
    private static volatile Boolean isProdCache = null;

    /**
     * 私有构造器，防止实例化
     */
    private SpringUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 判断当前环境是否为生产环境。
     *
     * <p>判断逻辑：
     * <ol>
     *   <li>优先从 Spring 的 {@link Environment} 获取所有激活的 Profiles，只要包含 "prod" 即返回 true。</li>
     *   <li>若获取 Environment 失败，则回退到 Hutool 的 {@link #getActiveProfile()}（仅返回第一个激活的 Profile）。</li>
     *   <li>结果会缓存，后续调用直接返回缓存值，提升性能。</li>
     * </ol>
     *
     * <p><b>注意：</b>当 Spring 环境动态变更时（例如单元测试中切换 Profile），应调用 {@link #refreshProdCache()}
     * 手动刷新缓存，否则判断结果可能不准确。
     *
     * @return 是否生产环境
     */
    public static boolean isProd() {
        // 快速检查缓存
        Boolean result = isProdCache;
        if (result != null) {
            return result;
        }

        // 双重检查锁定
        synchronized (SpringUtils.class) {
            // 再次检查缓存，避免重复初始化
            if (isProdCache != null) {
                return isProdCache;
            }

            // 优先从 Spring Environment 获取所有激活的 Profiles
            try {
                Environment environment = getBean(Environment.class);
                if (environment != null) {
                    String[] activeProfiles = environment.getActiveProfiles();
                    boolean isProd = Arrays.stream(activeProfiles).anyMatch("prod"::equals);
                    isProdCache = isProd;
                    if (log.isDebugEnabled()) {
                        log.debug("isProd: determined via Environment, activeProfiles={}, result={}",
                                Arrays.toString(activeProfiles), isProd);
                    }
                    return isProd;
                } else {
                    log.warn("isProd: Environment bean is null, falling back to Hutool method");
                }
            } catch (Exception e) {
                // 记录异常，但不中断，回退到 Hutool 方法
                log.warn("isProd: failed to get Environment bean, falling back to Hutool method", e);
            }

            // 回退方案：获取第一个激活的 Profile（Hutool 方式）
            String activeProfile = getActiveProfile();
            boolean isProd = "prod".equals(activeProfile);
            isProdCache = isProd;
            if (log.isDebugEnabled()) {
                log.debug("isProd: determined via Hutool getActiveProfile(), activeProfile={}, result={}",
                        activeProfile, isProd);
            }
            return isProd;
        }
    }

    /**
     * 刷新生产环境判断的缓存。
     *
     * <p>当 Spring 环境动态变更（例如单元测试中切换 Profile）时，调用此方法可强制重新判断环境。
     */
    public static void refreshProdCache() {
        synchronized (SpringUtils.class) {
            isProdCache = null;
            if (log.isDebugEnabled()) {
                log.debug("refreshProdCache: cache cleared");
            }
        }
    }
}
