package cn.realm.cloud.framework.common.util.collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内存关联工具类（In-Memory Join Utilities）。
 *
 * <p><b>核心价值：</b>解决 ORM 框架常见的 N+1 查询问题，
 * 将循环内的多次单条查询合并为批量查询 + 内存 Map 关联。
 *
 * <p><b>线程安全：</b>本工具类所有静态方法均为无状态设计，线程安全。
 * 异步并行场景（{@link #joinParallel}）下，调用方传入的 {@code queryFunc}
 * 与 {@code merger} 必须自身线程安全。
 *
 * <p><b>能力概览：</b>
 * <ul>
 *     <li><b>静态方法</b>：{@link #extractKeys}、{@link #joinMap}、{@link #joinMultiMap}，
 *         适合简单场景</li>
 *     <li><b>Step Builder</b>：{@link #on(List)} 链式调用，编译期强制泛型对齐</li>
 *     <li><b>异步并行</b>：{@link #joinMapAsync} + {@link #joinParallel}，
 *         多维度并行加载，总耗时降为 Max(T1, T2, ...)</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 方式一：静态方法
 * Set<Long> userIds = InMemoryJoinUtils.extractKeys(orders, Order::getUserId);
 * Map<Long, User> userMap = InMemoryJoinUtils.joinMap(
 *         userIds, userMapper::selectBatchIds, User::getId);
 *
 * // 方式二：Step Builder（推荐）
 * InMemoryJoinUtils.on(orders)
 *         .extract(Order::getUserId)
 *         .load(userMapper::selectBatchIds, User::getId)
 *         .merge((order, user) -> order.setUserName(user.getName()))
 *         .execute();
 *
 * // 方式三：一对多
 * InMemoryJoinUtils.on(orders)
 *         .extract(Order::getOrderId)
 *         .load(orderMapper::selectItemsByOrderIds, OrderItem::getOrderId)
 *         .mergeList((order, items) -> order.setItems(items))
 *         .execute();
 * }</pre>
 *
 * @author QI Guang
 */
public final class InMemoryJoinUtils {

    private static final Logger log = LoggerFactory.getLogger(InMemoryJoinUtils.class);

    /**
     * 默认批处理大小，可通过系统属性 {@code -Din-memory-join.batch-size=2000} 覆盖。
     * <p>用于避免 MySQL {@code IN (...)} 子句过长或单次查询内存溢出。
     */
    private static final int DEFAULT_BATCH_SIZE =
            Integer.getInteger("in-memory-join.batch-size", 1000);

    /**
     * 慢查询阈值（毫秒），超过此值记录 WARN 日志。
     */
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000L;

    /**
     * 大集合告警阈值，超过此数量记录 INFO 日志。
     */
    private static final int LARGE_COLLECTION_THRESHOLD = 5000;

    /**
     * 提取 Key 时启用"传统 for 循环"而非 Stream 的阈值。
     * <p>大数据量下 Stream 拆箱/装箱开销高于 for 循环。
     */
    private static final int STREAM_FALLBACK_THRESHOLD = 1000;

    private InMemoryJoinUtils() {
        throw new UnsupportedOperationException(
                "InMemoryJoinUtils is a static utility class and cannot be instantiated");
    }

    // ==================== 一、静态工具方法 ====================

    /**
     * 从对象列表中提取指定属性的 Key 集合，自动去重并过滤 {@code null}。
     *
     * @param list      对象列表（可为 {@code null}）
     * @param keyMapper 提取函数
     * @param <T>       对象类型
     * @param <K>       Key 类型
     * @return 去重后的 Key 集合（{@link LinkedHashSet} 保序），永不为 {@code null}
     */
    public static <T, K> Set<K> extractKeys(List<T> list, Function<T, K> keyMapper) {
        if (list == null || list.isEmpty()) {
            return new LinkedHashSet<>();
        }
        // 统一使用传统 for 循环：大数据量下性能优于 Stream，且逻辑更直观
        Set<K> result = new LinkedHashSet<>(hashCapacity(list.size()));
        for (T item : list) {
            if (item != null) {
                K key = keyMapper.apply(item);
                if (key != null) {
                    result.add(key);
                }
            }
        }
        return result;
    }

    /**
     * 批量查询并组装为 {@code Map<K, V>}（一对一）。
     *
     * @param keys         Key 集合（可为 {@code null}）
     * @param queryFunc    批量查询函数
     * @param keyExtractor 从实体中提取 Key 的函数
     * @param <K>          Key 类型
     * @param <V>          实体类型
     * @return {@link LinkedHashMap}，永不为 {@code null}
     */
    public static <K, V> Map<K, V> joinMap(Collection<K> keys,
                                           Function<Collection<K>, List<V>> queryFunc,
                                           Function<V, K> keyExtractor) {
        return joinMap(keys, queryFunc, keyExtractor, DEFAULT_BATCH_SIZE);
    }

    /**
     * 批量查询并组装为 {@code Map<K, V>}（支持自定义分批大小）。
     */
    public static <K, V> Map<K, V> joinMap(Collection<K> keys,
                                           Function<Collection<K>, List<V>> queryFunc,
                                           Function<V, K> keyExtractor,
                                           int batchSize) {
        List<K> keyList = normalizeKeys(keys);
        if (keyList.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (keyList.size() > LARGE_COLLECTION_THRESHOLD) {
            log.info("[joinMap] large keys, size={}, batchSize={}", keyList.size(), batchSize);
        }

        // 不分批
        if (keyList.size() <= batchSize) {
            return doJoinMap(keyList, queryFunc, keyExtractor);
        }

        // 分批查询并合并
        Map<K, V> resultMap = new LinkedHashMap<>(hashCapacity(keyList.size()));
        for (int i = 0; i < keyList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keyList.size());
            List<K> batchKeys = keyList.subList(i, end);
            Map<K, V> batchMap = doJoinMap(batchKeys, queryFunc, keyExtractor);
            // 保留首次出现的值（主数据顺序优先）
            batchMap.forEach(resultMap::putIfAbsent);
        }
        return resultMap;
    }

    /**
     * 批量查询并组装为 {@code Map<K, List<V>>}（一对多）。
     */
    public static <K, V> Map<K, List<V>> joinMultiMap(Collection<K> keys,
                                                      Function<Collection<K>, List<V>> queryFunc,
                                                      Function<V, K> keyExtractor) {
        return joinMultiMap(keys, queryFunc, keyExtractor, DEFAULT_BATCH_SIZE);
    }

    /**
     * 批量查询并组装为 {@code Map<K, List<V>>}（支持自定义分批大小）。
     */
    public static <K, V> Map<K, List<V>> joinMultiMap(Collection<K> keys,
                                                      Function<Collection<K>, List<V>> queryFunc,
                                                      Function<V, K> keyExtractor,
                                                      int batchSize) {
        List<K> keyList = normalizeKeys(keys);
        if (keyList.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (keyList.size() > LARGE_COLLECTION_THRESHOLD) {
            log.info("[joinMultiMap] large keys, size={}, batchSize={}", keyList.size(), batchSize);
        }

        if (keyList.size() <= batchSize) {
            return doGroupByKey(executeQuery(keyList, queryFunc), keyExtractor);
        }

        Map<K, List<V>> resultMap = new LinkedHashMap<>(hashCapacity(keyList.size()));
        for (int i = 0; i < keyList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keyList.size());
            List<K> batchKeys = keyList.subList(i, end);
            Map<K, List<V>> batchMap = doGroupByKey(
                    executeQuery(batchKeys, queryFunc), keyExtractor);
            batchMap.forEach((k, list) ->
                    resultMap.computeIfAbsent(k, kk -> new ArrayList<>()).addAll(list));
        }
        return resultMap;
    }

    // ==================== 二、异步并行 ====================

    /**
     * 生成异步加载任务（使用默认的 {@link ForkJoinPool#commonPool()}）。
     *
     * <p><b>生产环境建议：</b>使用
     * {@link #joinMapAsync(Collection, Function, Function, Executor)}
     * 传入专用线程池，避免 commonPool 被其他任务争抢。
     */
    public static <K, V> CompletableFuture<Map<K, V>> joinMapAsync(
            Collection<K> keys,
            Function<Collection<K>, List<V>> queryFunc,
            Function<V, K> keyExtractor) {
        return joinMapAsync(keys, queryFunc, keyExtractor, ForkJoinPool.commonPool());
    }

    /**
     * 生成异步加载任务（支持自定义线程池）。
     */
    public static <K, V> CompletableFuture<Map<K, V>> joinMapAsync(
            Collection<K> keys,
            Function<Collection<K>, List<V>> queryFunc,
            Function<V, K> keyExtractor,
            Executor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        return CompletableFuture.supplyAsync(
                () -> joinMap(keys, queryFunc, keyExtractor), executor);
    }

    /**
     * 合并多个异步加载任务的结果。
     *
     * <p><b>异常聚合：</b>任一任务失败时，等待所有任务完成，
     * 通过 {@link Throwable#addSuppressed(Throwable)} 聚合所有异常后抛出。
     *
     * <p><b>Key 冲突：</b>多个任务返回的 Map 若存在相同 Key，<b>后者覆盖前者</b>。
     * 建议不同任务的 Key 域不重叠；若无法保证，请勿使用本方法。
     *
     * @param tasks 多个异步任务
     * @return 合并后的 Map
     */
    @SafeVarargs
    public static <K, V> Map<K, V> joinParallel(CompletableFuture<Map<K, V>>... tasks) {
        if (tasks == null || tasks.length == 0) {
            return new LinkedHashMap<>();
        }

        // 等待全部完成（异常场景下不会中断等待）
        try {
            CompletableFuture.allOf(tasks).join();
        } catch (Throwable primaryFailure) {
            // 明确语义：allOf 抛异常说明至少有一个任务失败
            // 下面聚合所有任务的异常
            Throwable aggregated = primaryFailure;
            for (CompletableFuture<Map<K, V>> task : tasks) {
                if (task.isCompletedExceptionally()) {
                    try {
                        task.join();
                    } catch (Throwable e) {
                        if (e != primaryFailure) {
                            aggregated.addSuppressed(e);
                        }
                    }
                }
            }
            throw new IllegalStateException(
                    "Parallel join failed, taskCount=" + tasks.length, aggregated);
        }

        // 合并结果
        Map<K, V> merged = new LinkedHashMap<>();
        for (CompletableFuture<Map<K, V>> task : tasks) {
            Map<K, V> map = task.join();
            if (map != null) {
                merged.putAll(map);
            }
        }
        return merged;
    }

    // ==================== 三、Map 安全取值（保留项目约定） ====================

    /**
     * 安全地从 Map 中取值，若 Map 或 Key 为 {@code null} 则返回默认值。
     *
     * <p><b>与 JDK {@link Map#getOrDefault} 的区别：</b>本方法额外处理 Map 为 {@code null} 的情况。
     * 若已确保 Map 非 {@code null}，请直接使用 JDK 原生 API。
     */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        V value = map.get(key);
        return value != null ? value : defaultValue;
    }

    // ==================== 四、List ↔ Map 转换 ====================

    /**
     * 将 List 转为 Map（值为主对象本身）。
     */
    public static <T, K> Map<K, T> listToMap(List<T> list, Function<T, K> keyExtractor) {
        return listToMap(list, keyExtractor, Function.identity());
    }

    /**
     * 将 List 转为 Map（值由 {@code valueExtractor} 提取）。
     *
     * <p><b>Key 冲突：</b>保留首次出现的值。
     */
    public static <T, K, V> Map<K, V> listToMap(List<T> list,
                                                Function<T, K> keyExtractor,
                                                Function<T, V> valueExtractor) {
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        if (list == null || list.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .filter(t -> keyExtractor.apply(t) != null)
                .collect(Collectors.toMap(
                        keyExtractor,
                        valueExtractor,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new));
    }

    /**
     * 按分类函数分组。
     */
    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> classifier) {
        Objects.requireNonNull(classifier, "classifier must not be null");
        if (list == null || list.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .filter(t -> classifier.apply(t) != null)
                .collect(Collectors.groupingBy(
                        classifier, LinkedHashMap::new, Collectors.toList()));
    }

    // ==================== 五、Step Builder ====================

    /**
     * 开始链式关联操作。
     *
     * @param mainData 主数据列表（可为 {@code null}）
     * @param <T>      主数据类型
     * @return 提取步骤（{@link ExtractStep}）
     */
    public static <T> ExtractStep<T> on(List<T> mainData) {
        return new ExtractStepImpl<>(mainData != null ? mainData : Collections.emptyList());
    }

    /** 提取步骤：从主数据中提取 Key。 */
    public interface ExtractStep<T> {
        /**
         * 指定 Key 提取函数。
         */
        <K> LoadStep<T, K> extract(Function<T, K> keyExtractor);
    }

    /** 加载步骤：指定批量查询函数与结果 Key 提取函数。 */
    public interface LoadStep<T, K> {
        /**
         * 指定批量加载方法。
         *
         * @param queryFunc          批量查询函数（入参 {@code Collection<K>}）
         * @param resultKeyExtractor 从结果实体中提取 Key 的函数
         */
        <V> MergeStep<T, K, V> load(Function<Collection<K>, List<V>> queryFunc,
                                    Function<V, K> resultKeyExtractor);
    }

    /** 合并步骤：指定合并策略并执行。 */
    public interface MergeStep<T, K, V> {
        /**
         * 一对一合并（订单 → 用户）。
         * <p>若关联数据缺失，不会调用 {@code merger}。
         */
        MergeStep<T, K, V> merge(BiConsumer<T, V> merger);

        /**
         * 一对多合并（订单 → 商品列表）。
         * <p>即使关联数据为空，也会以空列表调用 {@code merger}。
         */
        MergeStep<T, K, V> mergeList(BiConsumer<T, List<V>> merger);

        /**
         * 执行关联，原地修改主数据列表。
         *
         * @return 主数据列表（与传入列表为同一对象）
         */
        List<T> execute();
    }

    // ==================== 六、Builder 内部实现 ====================

    private static final class ExtractStepImpl<T> implements ExtractStep<T> {
        private final List<T> mainData;

        ExtractStepImpl(List<T> mainData) {
            this.mainData = mainData;
        }

        @Override
        public <K> LoadStep<T, K> extract(Function<T, K> keyExtractor) {
            Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
            return new LoadStepImpl<>(mainData, keyExtractor);
        }
    }

    private static final class LoadStepImpl<T, K> implements LoadStep<T, K> {
        private final List<T> mainData;
        private final Function<T, K> keyExtractor;

        LoadStepImpl(List<T> mainData, Function<T, K> keyExtractor) {
            this.mainData = mainData;
            this.keyExtractor = keyExtractor;
        }

        @Override
        public <V> MergeStep<T, K, V> load(Function<Collection<K>, List<V>> queryFunc,
                                           Function<V, K> resultKeyExtractor) {
            Objects.requireNonNull(queryFunc, "queryFunc must not be null");
            Objects.requireNonNull(resultKeyExtractor, "resultKeyExtractor must not be null");
            return new MergeStepImpl<>(mainData, keyExtractor, queryFunc, resultKeyExtractor);
        }
    }

    private static final class MergeStepImpl<T, K, V> implements MergeStep<T, K, V> {
        private final List<T> mainData;
        private final Function<T, K> keyExtractor;
        private final Function<Collection<K>, List<V>> queryFunc;
        private final Function<V, K> resultKeyExtractor;

        private BiConsumer<T, V> oneMerger;
        private BiConsumer<T, List<V>> manyMerger;
        private boolean isOne = true;

        MergeStepImpl(List<T> mainData,
                      Function<T, K> keyExtractor,
                      Function<Collection<K>, List<V>> queryFunc,
                      Function<V, K> resultKeyExtractor) {
            this.mainData = mainData;
            this.keyExtractor = keyExtractor;
            this.queryFunc = queryFunc;
            this.resultKeyExtractor = resultKeyExtractor;
        }

        @Override
        public MergeStep<T, K, V> merge(BiConsumer<T, V> merger) {
            this.oneMerger = Objects.requireNonNull(merger, "merger must not be null");
            this.isOne = true;
            return this;
        }

        @Override
        public MergeStep<T, K, V> mergeList(BiConsumer<T, List<V>> merger) {
            this.manyMerger = Objects.requireNonNull(merger, "merger must not be null");
            this.isOne = false;
            return this;
        }

        @Override
        public List<T> execute() {
            if (mainData.isEmpty()) {
                return mainData;
            }
            Set<K> keys = extractKeys(mainData, keyExtractor);
            if (keys.isEmpty()) {
                return mainData;
            }
            if (isOne) {
                executeOneToOne(keys);
            } else {
                executeOneToMany(keys);
            }
            return mainData;
        }

        private void executeOneToOne(Set<K> keys) {
            Map<K, V> map = joinMap(keys, queryFunc, resultKeyExtractor);
            for (T item : mainData) {
                if (item == null) {
                    continue;
                }
                K key = keyExtractor.apply(item);
                if (key == null) {
                    continue;
                }
                V value = map.get(key);
                if (value != null) {
                    oneMerger.accept(item, value);
                }
            }
        }

        private void executeOneToMany(Set<K> keys) {
            Map<K, List<V>> map = joinMultiMap(keys, queryFunc, resultKeyExtractor);
            for (T item : mainData) {
                if (item == null) {
                    continue;
                }
                K key = keyExtractor.apply(item);
                if (key == null) {
                    continue;
                }
                List<V> list = map.get(key);
                manyMerger.accept(item, list != null ? list : Collections.emptyList());
            }
        }
    }

    // ==================== 七、私有辅助方法 ====================

    /**
     * 规范化 Key 集合：过滤 {@code null}，去重。
     * <p>Set 输入时跳过去重，List 输入时使用 Stream 去重。
     */
    private static <K> List<K> normalizeKeys(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        if (keys instanceof Set) {
            List<K> keyList = new ArrayList<>(keys);
            keyList.removeIf(Objects::isNull);
            return keyList;
        }
        return keys.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 单次批量查询并转为 Map（Key 冲突时保留首次出现的值）。
     */
    private static <K, V> Map<K, V> doJoinMap(Collection<K> keys,
                                              Function<Collection<K>, List<V>> queryFunc,
                                              Function<V, K> keyExtractor) {
        List<V> resultList = executeQuery(keys, queryFunc);
        if (resultList == null || resultList.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return resultList.stream()
                .filter(Objects::nonNull)
                .filter(v -> keyExtractor.apply(v) != null)
                .collect(Collectors.toMap(
                        keyExtractor,
                        Function.identity(),
                        (existing, replacement) -> existing,
                        () -> new LinkedHashMap<>(hashCapacity(keys.size()))));
    }

    /**
     * 按 Key 分组。
     */
    private static <K, V> Map<K, List<V>> doGroupByKey(List<V> resultList,
                                                       Function<V, K> keyExtractor) {
        if (resultList == null || resultList.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return resultList.stream()
                .filter(Objects::nonNull)
                .filter(v -> keyExtractor.apply(v) != null)
                .collect(Collectors.groupingBy(
                        keyExtractor, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * 执行查询，统一处理耗时监控与异常语义。
     *
     * <p><b>异常策略：</b>直接抛出原始异常（不包装），由上层决定如何处理。
     * 这避免了"异常双重报告"（Effective Java Item 77）。
     */
    private static <K, V> List<V> executeQuery(Collection<K> keys,
                                               Function<Collection<K>, List<V>> queryFunc) {
        long start = System.nanoTime();
        List<V> result = queryFunc.apply(keys);
        long costMs = (System.nanoTime() - start) / 1_000_000;
        if (costMs > SLOW_QUERY_THRESHOLD_MS) {
            log.warn("[executeQuery] slow query, keySize={}, costMs={}", keys.size(), costMs);
        } else if (log.isDebugEnabled()) {
            log.debug("[executeQuery] success, keySize={}, costMs={}", keys.size(), costMs);
        }
        return result;
    }

    /**
     * 根据预期元素数量计算 HashMap 初始容量，避免扩容。
     * <p>公式：{@code expectedSize / 0.75 + 1}（HashMap 负载因子 0.75）。
     */
    private static int hashCapacity(int expectedSize) {
        if (expectedSize <= 0) {
            return 0;
        }
        return (int) (expectedSize / 0.75f) + 1;
    }
}
