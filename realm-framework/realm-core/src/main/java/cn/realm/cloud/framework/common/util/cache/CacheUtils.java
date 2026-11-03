package cn.realm.cloud.framework.common.util.cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Guava Cache 工具类，提供异步/同步刷新缓存的便捷构建方法。
 *
 * <p><b>能力：</b>
 * <ul>
 *     <li>{@link #buildCache}：同步刷新（阻塞读取线程），适用于与 ThreadLocal 关联的缓存</li>
 *     <li>{@link #buildAsyncReloadingCache}：异步刷新（返回旧值），适用于全局无状态缓存</li>
 * </ul>
 *
 * <p><b>默认容量：</b>{@code 10000}，可通过系统属性 {@code -Dcache.max.size=20000} 覆盖。
 *
 * <p><b>线程管理：</b>默认刷新线程池使用<b>守护线程</b>，JVM 可正常退出。
 * 如需自定义线程池，应确保其生命周期与 Spring 容器绑定（通过 {@code @PreDestroy} 关闭）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 同步刷新（推荐用于依赖 ThreadLocal 的场景）
 * LoadingCache<String, Config> cache = CacheUtils.buildCache(
 *         Duration.ofMinutes(5),
 *         key -> configService.load(key));
 *
 * // 异步刷新（推荐用于无状态全局缓存）
 * LoadingCache<String, Dict> dictCache = CacheUtils.buildAsyncReloadingCache(
 *         Duration.ofMinutes(10),
 *         key -> dictService.load(key));
 * }</pre>
 *
 * @author QI Guang
 */
public final class CacheUtils {

    private static final Logger log = LoggerFactory.getLogger(CacheUtils.class);

    /**
     * 默认最大缓存条目数，可通过系统属性 {@code -Dcache.max.size=20000} 覆盖。
     */
    private static final int DEFAULT_MAX_SIZE = Integer.getInteger("cache.max.size", 10_000);

    /**
     * 默认异步刷新线程池。
     *
     * <p><b>守护线程：</b>使用守护线程，避免阻止 JVM 退出。
     * 缓存刷新是"尽力而为"的后台任务，不应阻止应用关闭。
     *
     * <p><b>线程命名：</b>前缀 {@code cache-refresh}，便于 JVM 线程 dump 时定位。
     */
    private static final ExecutorService DEFAULT_REFRESH_EXECUTOR =
            Executors.newCachedThreadPool(
                    new ThreadFactoryBuilder()
                            .setNameFormat("cache-refresh-%d")
                            .setDaemon(true)
                            .build());

    /**
     * 私有构造器，防止实例化。
     */
    private CacheUtils() {
        throw new UnsupportedOperationException(
                "CacheUtils is a static utility class and cannot be instantiated");
    }

    // ==================== 同步刷新 ====================

    /**
     * 构建同步刷新的 {@link LoadingCache}（使用默认最大容量）。
     *
     * <p>缓存刷新时<b>阻塞当前读取线程</b>，直至加载完成。
     * 适用于加载逻辑依赖 ThreadLocal 的场景（如用户上下文、租户上下文）。
     *
     * @param duration 刷新间隔（自最后一次写入起计时），不允许为 {@code null}
     * @param loader   加载逻辑，不允许为 {@code null}
     * @param <K>      键类型
     * @param <V>      值类型
     * @return {@link LoadingCache} 实例
     */
    public static <K, V> LoadingCache<K, V> buildCache(Duration duration, CacheLoader<K, V> loader) {
        return buildCache(DEFAULT_MAX_SIZE, duration, loader);
    }

    /**
     * 构建同步刷新的 {@link LoadingCache}（自定义最大容量）。
     *
     * @param maxSize  最大缓存条目数，必须为正整数
     * @param duration 刷新间隔，不允许为 {@code null}
     * @param loader   加载逻辑，不允许为 {@code null}
     * @param <K>      键类型
     * @param <V>      值类型
     * @return {@link LoadingCache} 实例
     */
    public static <K, V> LoadingCache<K, V> buildCache(int maxSize, Duration duration,
                                                       CacheLoader<K, V> loader) {
        requirePositive(maxSize, "maxSize");
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(loader, "loader must not be null");

        if (log.isDebugEnabled()) {
            log.debug("[buildCache] maxSize={}, refreshAfterWrite={}", maxSize, duration);
        }

        long start = System.nanoTime();
        LoadingCache<K, V> cache = CacheBuilder.newBuilder()
                .maximumSize(maxSize)
                .refreshAfterWrite(duration)
                .build(loader);

        if (log.isDebugEnabled()) {
            log.debug("[buildCache] built in {}ns", System.nanoTime() - start);
        }
        return cache;
    }

    // ==================== 异步刷新 ====================

    /**
     * 构建异步刷新的 {@link LoadingCache}（使用默认线程池）。
     *
     * <p>缓存刷新时<b>不阻塞读取线程</b>，立即返回旧值，刷新任务提交到默认线程池异步执行。
     * 适用于全局、无状态、加载较快的缓存（如系统字典、配置项）。
     *
     * <p><b>注意：</b>异步线程<b>无法继承 ThreadLocal</b>。
     * 若加载逻辑依赖 ThreadLocal（如用户上下文），请使用 {@link #buildCache(Duration, CacheLoader)}。
     *
     * @param duration 刷新间隔，不允许为 {@code null}
     * @param loader   加载逻辑，不允许为 {@code null}
     * @param <K>      键类型
     * @param <V>      值类型
     * @return {@link LoadingCache} 实例
     */
    public static <K, V> LoadingCache<K, V> buildAsyncReloadingCache(Duration duration,
                                                                     CacheLoader<K, V> loader) {
        return buildAsyncReloadingCache(duration, loader, DEFAULT_REFRESH_EXECUTOR);
    }

    /**
     * 构建异步刷新的 {@link LoadingCache}（自定义线程池）。
     *
     * <p><b>线程池生命周期：</b>调用方负责关闭自定义线程池。
     * 推荐通过 Spring 的 {@code @PreDestroy} 或 {@code DisposableBean} 在容器关闭时调用
     * {@link ExecutorService#shutdown()}，避免线程泄漏。
     *
     * @param duration 刷新间隔，不允许为 {@code null}
     * @param loader   加载逻辑，不允许为 {@code null}
     * @param executor 异步加载线程池，不允许为 {@code null}
     * @param <K>      键类型
     * @param <V>      值类型
     * @return {@link LoadingCache} 实例
     */
    public static <K, V> LoadingCache<K, V> buildAsyncReloadingCache(Duration duration,
                                                                     CacheLoader<K, V> loader,
                                                                     ExecutorService executor) {
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(loader, "loader must not be null");
        Objects.requireNonNull(executor, "executor must not be null");

        if (log.isDebugEnabled()) {
            log.debug("[buildAsyncReloadingCache] maxSize={}, refreshAfterWrite={}, executor={}",
                    DEFAULT_MAX_SIZE, duration, executor.getClass().getSimpleName());
        }

        long start = System.nanoTime();
        LoadingCache<K, V> cache = CacheBuilder.newBuilder()
                .maximumSize(DEFAULT_MAX_SIZE)
                .refreshAfterWrite(duration)
                .build(CacheLoader.asyncReloading(loader, executor));

        if (log.isDebugEnabled()) {
            log.debug("[buildAsyncReloadingCache] built in {}ns", System.nanoTime() - start);
        }
        return cache;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 参数校验：要求 {@code value} 为正整数。
     *
     * @param value     待校验值
     * @param paramName 参数名（用于异常消息）
     * @throws IllegalArgumentException 当 {@code value <= 0} 时抛出
     */
    private static void requirePositive(int value, String paramName) {
        if (value <= 0) {
            throw new IllegalArgumentException(paramName + " must be positive, but was: " + value);
        }
    }
}
