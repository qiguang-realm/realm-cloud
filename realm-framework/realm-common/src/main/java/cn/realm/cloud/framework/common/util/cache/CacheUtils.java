package cn.realm.cloud.framework.common.util.cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guava Cache 工具类，提供异步/同步刷新缓存的便捷构建方法。
 *
 * <p>该类封装了常用的缓存构建模式，支持最大容量（默认 10000）和刷新时间配置。
 *
 * @author QI Guang
 */
public class CacheUtils {

    private static final Logger log = LoggerFactory.getLogger(CacheUtils.class);

    /**
     * 默认最大缓存条目数（可通过系统属性或配置文件调整）。
     * 如需修改，建议在应用启动时设置系统属性：-Dcache.max.size=20000
     */
    private static final int CACHE_MAX_SIZE = Integer.getInteger("cache.max.size", 10000);

    /**
     * 默认异步刷新线程池（使用带命名前缀的缓存线程池，便于监控）。
     * 注意：该线程池无界，适用于缓存加载操作较轻的场景；若加载任务较重，建议改用固定线程池。
     */
    private static final ExecutorService DEFAULT_REFRESH_EXECUTOR =
            Executors.newCachedThreadPool(new CacheThreadFactory("cache-refresh"));

    /**
     * 私有构造器，防止实例化。
     */
    private CacheUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 构建异步刷新的 LoadingCache 对象。
     *
     * <p>当缓存过期时，异步加载新值，返回旧值，避免阻塞读取线程。
     * 适用于全局性、无 ThreadLocal 依赖的缓存（例如系统配置、字典数据）。
     *
     * <p><b>注意：</b>若缓存加载涉及 ThreadLocal（如用户上下文），建议使用 {@link #buildCache(Duration, CacheLoader)}，
     * 因为异步线程无法继承 ThreadLocal，可能导致上下文丢失。
     *
     * @param duration 刷新间隔（从最后一次写入后开始计时）
     * @param loader   CacheLoader 加载逻辑
     * @param <K>      键类型
     * @param <V>      值类型
     * @return LoadingCache 实例
     */
    public static <K, V> LoadingCache<K, V> buildAsyncReloadingCache(Duration duration, CacheLoader<K, V> loader) {
        return buildAsyncReloadingCache(duration, loader, DEFAULT_REFRESH_EXECUTOR);
    }

    /**
     * 构建异步刷新的 LoadingCache 对象，支持自定义线程池。
     *
     * @param duration 刷新间隔
     * @param loader   CacheLoader 加载逻辑
     * @param executor 用于异步加载的线程池（建议使用固定大小，避免无界增长）
     * @param <K>      键类型
     * @param <V>      值类型
     * @return LoadingCache 实例
     */
    public static <K, V> LoadingCache<K, V> buildAsyncReloadingCache(Duration duration,
                                                                     CacheLoader<K, V> loader,
                                                                     ExecutorService executor) {
        if (duration == null || loader == null || executor == null) {
            throw new IllegalArgumentException("duration, loader and executor must not be null");
        }
        log.info("Building async reloading cache with maxSize={}, refreshAfterWrite={}, executor={}",
                CACHE_MAX_SIZE, duration, executor.getClass().getSimpleName());
        long start = System.currentTimeMillis();
        try {
            LoadingCache<K, V> cache = CacheBuilder.newBuilder()
                    .maximumSize(CACHE_MAX_SIZE)
                    .refreshAfterWrite(duration)
                    .build(CacheLoader.asyncReloading(loader, executor));
            log.debug("Async reloading cache built successfully in {}ms", System.currentTimeMillis() - start);
            return cache;
        } catch (Exception e) {
            log.error("Failed to build async reloading cache", e);
            throw new RuntimeException("Cache build failed", e);
        }
    }

    /**
     * 构建同步刷新的 LoadingCache 对象。
     *
     * <p>当缓存过期时，同步加载新值，阻塞当前读取线程直至加载完成。
     * 适用于与 ThreadLocal 关联的缓存（如用户会话数据），确保加载线程的上下文正确传递。
     *
     * @param duration 刷新间隔
     * @param loader   CacheLoader 加载逻辑
     * @param <K>      键类型
     * @param <V>      值类型
     * @return LoadingCache 实例
     */
    public static <K, V> LoadingCache<K, V> buildCache(Duration duration, CacheLoader<K, V> loader) {
        if (duration == null || loader == null) {
            throw new IllegalArgumentException("duration and loader must not be null");
        }
        log.info("Building sync reloading cache with maxSize={}, refreshAfterWrite={}", CACHE_MAX_SIZE, duration);
        long start = System.currentTimeMillis();
        try {
            LoadingCache<K, V> cache = CacheBuilder.newBuilder()
                    .maximumSize(CACHE_MAX_SIZE)
                    .refreshAfterWrite(duration)
                    .build(loader);
            log.debug("Sync reloading cache built successfully in {}ms", System.currentTimeMillis() - start);
            return cache;
        } catch (Exception e) {
            log.error("Failed to build sync reloading cache", e);
            throw new RuntimeException("Cache build failed", e);
        }
    }

    /**
     * 构建带最大条目数限制的同步刷新缓存（重载，支持自定义最大大小）。
     *
     * @param maxSize  最大缓存条目数
     * @param duration 刷新间隔
     * @param loader   CacheLoader 加载逻辑
     * @param <K>      键类型
     * @param <V>      值类型
     * @return LoadingCache 实例
     */
    public static <K, V> LoadingCache<K, V> buildCache(int maxSize, Duration duration, CacheLoader<K, V> loader) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        if (duration == null || loader == null) {
            throw new IllegalArgumentException("duration and loader must not be null");
        }
        log.info("Building sync reloading cache with maxSize={}, refreshAfterWrite={}", maxSize, duration);
        long start = System.currentTimeMillis();
        try {
            LoadingCache<K, V> cache = CacheBuilder.newBuilder()
                    .maximumSize(maxSize)
                    .refreshAfterWrite(duration)
                    .build(loader);
            log.debug("Sync reloading cache built successfully in {}ms", System.currentTimeMillis() - start);
            return cache;
        } catch (Exception e) {
            log.error("Failed to build sync reloading cache with maxSize={}", maxSize, e);
            throw new RuntimeException("Cache build failed", e);
        }
    }

    /**
     * 自定义线程工厂，为线程池中的线程提供有意义的名称，便于监控和排查。
     */
    private static class CacheThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        CacheThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-thread-" + threadNumber.getAndIncrement());
            if (t.isDaemon()) {
                t.setDaemon(false); // 确保线程非守护，避免JVM提前退出
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
