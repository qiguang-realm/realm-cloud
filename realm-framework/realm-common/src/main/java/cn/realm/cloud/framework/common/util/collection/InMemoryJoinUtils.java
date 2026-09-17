package cn.realm.cloud.framework.common.util.collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内存关联工具类 V2.0（In-Memory Join Utilities）
 *
 * <p>核心价值：解决 ORM 框架常见的 N+1 查询问题，将循环内多次单条查询转为批量查询 + 内存 Map 关联。</p>
 *
 * <p><b>线程安全说明：</b>本工具类所有静态方法均为无状态设计，线程安全。
 * 但在异步并行场景（{@link #joinMapAsync} / {@link #joinParallel}）下，
 * 调用方传入的 {@code queryFunc} 和 {@code merger} 必须自身保证线程安全。</p>
 *
 * <p>V2.0 新增特性：
 * <ul>
 *   <li><b>类型安全构建器</b>：采用 Step Builder 模式，编译期强制泛型对齐，消除 {@code SuppressWarnings} 隐患</li>
 *   <li><b>一对多关联</b>：支持 {@code mergeList}，轻松处理「订单 → 商品列表」等场景</li>
 *   <li><b>异步并行加载</b>：提供 {@code joinMapAsync}，多关联维度可并行查询，总耗时降为 Max(T1,T2,T3)</li>
 *   <li><b>可观测性埋点</b>：内置 Slf4j 日志，自动记录慢查询（>1s）和大批量（>5000）操作</li>
 *   <li><b>高性能优化</b>：自动识别 Set 去重、精准容量计算（/0.75f+1）、保留插入顺序（LinkedHashMap）</li>
 * </ul>
 * </p>
 *
 * <p><b>使用方式一：静态方法风格（适合简单场景）</b>
 * <pre>{@code
 * List<Order> orders = orderMapper.selectList(query);
 * Set<Long> userIds = InMemoryJoinUtils.extractKeys(orders, Order::getUserId);
 * Map<Long, User> userMap = InMemoryJoinUtils.joinMap(userIds, userMapper::selectBatchIds, User::getId);
 * }</pre>
 *
 * <p><b>使用方式二：类型安全构建器（推荐，支持链式一对一/一对多）</b>
 * <pre>{@code
 * // 一对一关联（订单 -> 用户）
 * InMemoryJoinUtils.on(orders)
 *         .extract(Order::getUserId)
 *         .load(userMapper::selectBatchIds, User::getId)
 *         .merge((order, user) -> order.setUserName(user.getName()))
 *         .execute();
 *
 * // 一对多关联（订单 -> 商品列表）
 * InMemoryJoinUtils.on(orders)
 *         .extract(Order::getOrderId)
 *         .load(orderMapper::selectItemsByOrderIds, OrderItem::getOrderId)
 *         .mergeList((order, items) -> order.setItems(items))
 *         .execute();
 * }</pre>
 *
 * @author QI Guang
 * @version 2.0.1
 */
public final class InMemoryJoinUtils {

    // 目标：提供现实世界的业务场景、用例以及具体的代码示例，说明如何使用该工具。

    // 自定义线程池

    private static final Logger log = LoggerFactory.getLogger(InMemoryJoinUtils.class);

    /**
     * 默认批处理大小（防止 MySQL IN 子句过长或内存溢出）
     */
    private static final int DEFAULT_BATCH_SIZE = 1000;

    /**
     * 慢查询阈值（毫秒），超过此值将打印 WARN 日志
     */
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000L;

    /**
     * 大集合告警阈值，超过此数量将打印 INFO 日志
     */
    private static final int LARGE_COLLECTION_THRESHOLD = 5000;

    // ==================== 私有构造器 ====================

    private InMemoryJoinUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // ==================== 一、静态工具方法（V2.0 增强） ====================

    /**
     * 从对象列表中提取指定属性的 Key 集合，自动去重并过滤 null。
     * <p>V2.0 优化：数据量超过 1000 时自动切换为传统 for 循环，避免 Stream 拆箱开销。</p>
     *
     * @param list      对象列表（可为 null）
     * @param keyMapper 提取函数
     * @param <T>       对象类型
     * @param <K>       Key 类型
     * @return 去重后的 Key 集合（LinkedHashSet 保序），绝不返回 null
     */
    public static <T, K> Set<K> extractKeys(List<T> list, Function<T, K> keyMapper) {
        if (list == null || list.isEmpty()) {
            return new LinkedHashSet<>();
        }

        // 大数据量下 Stream 性能略逊于传统 for，做阈值分流
        if (list.size() > 1000) {
            Set<K> result = new LinkedHashSet<>((int) (list.size() / 0.75f) + 1);
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

        return list.stream()
                .filter(Objects::nonNull)
                .map(keyMapper)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 批量查询并组装为 Map&lt;Key, 实体&gt;（一对一）。
     * <p>V2.0 优化：自动识别 Set 跳过重复去重；使用 LinkedHashMap 保序；精准容量计算。</p>
     *
     * @param keys         Key 集合（可为 null）
     * @param queryFunc    批量查询函数
     * @param keyExtractor 从实体中提取 Key 的函数
     * @param <K>          Key 类型
     * @param <V>          实体类型
     * @return Map，绝不返回 null
     */
    public static <K, V> Map<K, V> joinMap(
            Collection<K> keys,
            Function<Collection<K>, List<V>> queryFunc,
            Function<V, K> keyExtractor) {
        return joinMap(keys, queryFunc, keyExtractor, DEFAULT_BATCH_SIZE);
    }

    /**
     * 批量查询并组装为 Map（支持自定义分批大小）。
     */
    public static <K, V> Map<K, V> joinMap(
            Collection<K> keys,
            Function<Collection<K>, List<V>> queryFunc,
            Function<V, K> keyExtractor,
            int batchSize) {

        if (keys == null || keys.isEmpty()) {
            return new LinkedHashMap<>();
        }

        // 去重并过滤 null（优化 Set 识别，避免重复去重）
        List<K> keyList;
        if (keys instanceof Set) {
            // Set 本身不允许 null，但安全起见仍过滤
            keyList = new ArrayList<>((Set<K>) keys);
            keyList.removeIf(Objects::isNull);
        } else {
            keyList = keys.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        }

        if (keyList.isEmpty()) {
            return new LinkedHashMap<>();
        }

        // 大集合告警
        if (keyList.size() > LARGE_COLLECTION_THRESHOLD) {
            log.info("InMemoryJoin joinMap with large keys, size={}, batchSize={}", keyList.size(), batchSize);
        }

        // 若数量不超过 batchSize，直接查询
        if (keyList.size() <= batchSize) {
            return doJoinMap(keyList, queryFunc, keyExtractor);
        }

        // 分批查询并合并
        Map<K, V> resultMap = new LinkedHashMap<>((int) (keyList.size() / 0.75f) + 1);
        for (int i = 0; i < keyList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keyList.size());
            List<K> batchKeys = keyList.subList(i, end);
            Map<K, V> batchMap = doJoinMap(batchKeys, queryFunc, keyExtractor);
            // 使用 putIfAbsent 保留首次出现的值（保持主数据顺序优先）
            batchMap.forEach(resultMap::putIfAbsent);
        }
        return resultMap;
    }

    /**
     * 批量查询并组装为 Map&lt;Key, List&lt;实体&gt;&gt;（一对多）。
     * <p>适用场景：订单批量查询商品列表、班级批量查询学生列表等。</p>
     */
    public static <K, V> Map<K, List<V>> joinMultiMap(
            Collection<K> keys,
            Function<Collection<K>, List<V>> queryFunc,
            Function<V, K> keyExtractor) {
        return joinMultiMap(keys, queryFunc, keyExtractor, DEFAULT_BATCH_SIZE);
    }

    /**
     * 批量查询并组装为 Map&lt;Key, List&lt;实体&gt;&gt;（支持自定义分批大小）。
     */
    public static <K, V> Map<K, List<V>> joinMultiMap(
            Collection<K> keys,
            Function<Collection<K>, List<V>> queryFunc,
            Function<V, K> keyExtractor,
            int batchSize) {

        if (keys == null || keys.isEmpty()) {
            return new LinkedHashMap<>();
        }

        // 去重过滤（优化 Set 识别）
        List<K> keyList;
        if (keys instanceof Set) {
            keyList = new ArrayList<>((Set<K>) keys);
            keyList.removeIf(Objects::isNull);
        } else {
            keyList = keys.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        }

        if (keyList.isEmpty()) {
            return new LinkedHashMap<>();
        }

        if (keyList.size() > LARGE_COLLECTION_THRESHOLD) {
            log.info("InMemoryJoin joinMultiMap with large keys, size={}, batchSize={}", keyList.size(), batchSize);
        }

        // 分批查询并合并
        Map<K, List<V>> resultMap = new LinkedHashMap<>((int) (keyList.size() / 0.75f) + 1);

        if (keyList.size() <= batchSize) {
            List<V> resultList = executeQuery(keyList, queryFunc);
            return doGroupByKey(resultList, keyExtractor);
        }

        for (int i = 0; i < keyList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keyList.size());
            List<K> batchKeys = keyList.subList(i, end);
            Map<K, List<V>> batchMap = doGroupByKey(
                    executeQuery(batchKeys, queryFunc),
                    keyExtractor
            );
            // 合并 List
            batchMap.forEach((k, list) ->
                    resultMap.computeIfAbsent(k, kk -> new ArrayList<>()).addAll(list)
            );
        }
        return resultMap;
    }

    /**
     * 异步并行加载多个关联 Map（V2.0 新增）。
     * <p>适用场景：一个列表需要同时关联 3~5 张表，使用 CompletableFuture 并发执行，总耗时从 T1+T2+... 降为 Max(T)。</p>
     * <p><b>注意：</b>若任一任务异常，将等待所有任务完成后，聚合所有异常并抛出（通过 {@code addSuppressed}）。</p>
     *
     * @param tasks 多个异步任务（通常由 {@code joinMapAsync} 生成）
     * @param <K>   Key 类型
     * @param <V>   值类型
     * @return 合并后的 Map（若 Key 冲突，后者覆盖前者，建议不同任务 Key 不重叠）
     */
    @SafeVarargs
    public static <K, V> Map<K, V> joinParallel(CompletableFuture<Map<K, V>>... tasks) {
        if (tasks == null || tasks.length == 0) {
            return new LinkedHashMap<>();
        }

        // 等待所有任务完成（异常时也会等待全部完成）
        CompletableFuture<Void> all = CompletableFuture.allOf(tasks);
        try {
            all.join();
        } catch (Throwable ignored) {
            // 收集所有任务异常并聚合抛出
            Throwable mainEx = null;
            for (CompletableFuture<Map<K, V>> task : tasks) {
                if (task.isCompletedExceptionally()) {
                    try {
                        task.join(); // 触发异常
                    } catch (Throwable e) {
                        if (mainEx == null) {
                            mainEx = e;
                        } else {
                            mainEx.addSuppressed(e);
                        }
                    }
                }
            }
            if (mainEx != null) {
                throw new RuntimeException("并行关联查询部分失败，共 " +
                        (mainEx.getSuppressed().length + 1) + " 个任务异常", mainEx);
            }
            // 理论上不会走到这里，但以防万一
            throw new RuntimeException("并行关联查询失败，但未捕获到具体异常");
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

    /**
     * 生成异步加载任务（使用默认的 ForkJoinPool.commonPool()）。
     * <p><b>生产环境建议：</b>使用 {@link #joinMapAsync(Collection, Function, Function, Executor)} 传入自定义线程池，避免 commonPool 被其他任务争抢。</p>
     */
    public static <K, V> CompletableFuture<Map<K, V>> joinMapAsync(
            Collection<K> keys,
            Function<Collection<K>, List<V>> queryFunc,
            Function<V, K> keyExtractor) {
        return joinMapAsync(keys, queryFunc, keyExtractor, ForkJoinPool.commonPool());
    }

    /**
     * 生成异步加载任务（支持自定义线程池）。
     *
     * @param executor 自定义线程池，推荐使用 {@code Executors.newFixedThreadPool} 等
     */
    public static <K, V> CompletableFuture<Map<K, V>> joinMapAsync(
            Collection<K> keys,
            Function<Collection<K>, List<V>> queryFunc,
            Function<V, K> keyExtractor,
            Executor executor) {
        return CompletableFuture.supplyAsync(() -> joinMap(keys, queryFunc, keyExtractor), executor);
    }

    // ==================== 二、Map 安全取值辅助方法 ====================

    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        V value = map.get(key);
        return value != null ? value : defaultValue;
    }

    public static <K, V> V safeGet(Map<K, V> map, K key) {
        if (map == null || key == null) {
            return null;
        }
        return map.get(key);
    }

    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    // ==================== 三、List ↔ Map 转换工具 ====================

    public static <T, K> Map<K, T> listToMap(List<T> list, Function<T, K> keyExtractor) {
        return listToMap(list, keyExtractor, null);
    }

    @SuppressWarnings("unchecked")
    public static <T, K, V> Map<K, V> listToMap(
            List<T> list,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {

        if (list == null || list.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return list.stream()
                .filter(Objects::nonNull)
                .filter(t -> keyExtractor.apply(t) != null)
                .collect(Collectors.toMap(
                        keyExtractor,
                        valueExtractor != null ? valueExtractor : t -> (V) t,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> classifier) {
        if (list == null || list.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .filter(t -> classifier.apply(t) != null)
                .collect(Collectors.groupingBy(
                        classifier,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    // ==================== 四、类型安全分步构建器（V2.0 核心革新） ====================

    /**
     * 开始链式关联操作，传入主数据列表。
     *
     * @param mainData 主数据列表
     * @param <T>      主数据类型
     * @return 提取步骤（ExtractStep）
     */
    public static <T> ExtractStep<T> on(List<T> mainData) {
        return new BuilderImpl<>(mainData != null ? mainData : Collections.emptyList());
    }

    // -------------------- 构建器接口定义（编译期类型安全） --------------------

    public interface ExtractStep<T> {
        /**
         * 指定从主数据中提取关联 Key 的函数。
         *
         * @param keyExtractor 提取函数
         * @param <K>          Key 类型
         * @return 加载步骤（LoadStep）
         */
        <K> LoadStep<T, K> extract(Function<T, K> keyExtractor);
    }

    public interface LoadStep<T, K> {
        /**
         * 指定批量加载关联数据的方法，并指定从关联实体中提取 Key 的函数。
         * <p>注意：此处 K 与 extract 中的 K 强绑定，编译期校验。</p>
         *
         * @param queryFunc          批量查询函数（入参 Collection&lt;K&gt;）
         * @param resultKeyExtractor 从关联实体中提取 Key 的函数
         * @param <V>                关联实体类型
         * @return 合并步骤（MergeStep）
         */
        <V> MergeStep<T, K, V> load(
                Function<Collection<K>, List<V>> queryFunc,
                Function<V, K> resultKeyExtractor
        );
    }

    public interface MergeStep<T, K, V> {
        /**
         * 一对一合并：将关联实体填充到主数据中（如 订单→用户）。
         * <p>若关联数据缺失，则不会调用 merger。</p>
         *
         * @param merger 合并函数 (主数据, 关联实体)
         * @return this
         */
        MergeStep<T, K, V> merge(BiConsumer<T, V> merger);

        /**
         * 一对多合并：将关联实体列表填充到主数据中（如 订单→商品列表）。
         *
         * @param merger 合并函数 (主数据, 关联实体列表)
         * @return this
         */
        MergeStep<T, K, V> mergeList(BiConsumer<T, List<V>> merger);

        /**
         * 执行关联操作，原地修改主数据列表。
         *
         * @return 主数据列表（与传入列表为同一对象）
         */
        List<T> execute();

        /**
         * 执行关联操作，并返回新构建的列表（不修改原列表）。
         * <p><b>重要提示：</b>此方法仅复制 List 引用，内部对象仍为同一实例，
         * 修改返回列表中的对象会影响原列表对象的状态。
         * 如需彻底的深拷贝，请业务层自行实现。</p>
         *
         * @return 新的主数据列表（浅拷贝）
         * @deprecated 语义易混淆，推荐直接使用 {@link #execute()} 原地修改，
         * 或业务层自行深拷贝。此方法可能在后续版本移除。
         */
        @Deprecated
        List<T> executeCopy();
    }

    // -------------------- 构建器内部实现 --------------------

    private static class BuilderImpl<T> implements ExtractStep<T> {
        private final List<T> mainData;

        BuilderImpl(List<T> mainData) {
            this.mainData = mainData;
        }

        @Override
        public <K> LoadStep<T, K> extract(Function<T, K> keyExtractor) {
            return new LoadStepImpl<>(mainData, keyExtractor);
        }
    }

    private static class LoadStepImpl<T, K> implements LoadStep<T, K> {
        private final List<T> mainData;
        private final Function<T, K> keyExtractor;

        LoadStepImpl(List<T> mainData, Function<T, K> keyExtractor) {
            this.mainData = mainData;
            this.keyExtractor = keyExtractor;
        }

        @Override
        public <V> MergeStep<T, K, V> load(
                Function<Collection<K>, List<V>> queryFunc,
                Function<V, K> resultKeyExtractor) {
            return new MergeStepImpl<>(mainData, keyExtractor, queryFunc, resultKeyExtractor);
        }
    }

    private static class MergeStepImpl<T, K, V> implements MergeStep<T, K, V> {
        private final List<T> mainData;
        private final Function<T, K> keyExtractor;
        private final Function<Collection<K>, List<V>> queryFunc;
        private final Function<V, K> resultKeyExtractor;

        private BiConsumer<T, V> oneMerger;
        private BiConsumer<T, List<V>> manyMerger;
        private boolean isOne = true; // 默认一对一

        MergeStepImpl(
                List<T> mainData,
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
            this.oneMerger = merger;
            this.isOne = true;
            return this;
        }

        @Override
        public MergeStep<T, K, V> mergeList(BiConsumer<T, List<V>> merger) {
            this.manyMerger = merger;
            this.isOne = false;
            return this;
        }

        @Override
        public List<T> execute() {
            if (mainData.isEmpty() || keyExtractor == null || queryFunc == null || resultKeyExtractor == null) {
                return mainData;
            }
            if ((isOne && oneMerger == null) || (!isOne && manyMerger == null)) {
                log.warn("InMemoryJoin execute called without proper merger, skipping.");
                return mainData;
            }

            // 1. 提取所有 Key（使用优化过的 extractKeys 逻辑）
            Set<K> keys = extractKeysInternal(mainData, keyExtractor);
            if (keys.isEmpty()) {
                return mainData;
            }

            // 2. 执行关联
            if (isOne) {
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
            } else {
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
                    // 即使 list 为空也传递空列表，让业务方决定如何处理
                    manyMerger.accept(item, list != null ? list : Collections.emptyList());
                }
            }

            return mainData;
        }

        @Override
        @Deprecated
        public List<T> executeCopy() {
            if (mainData.isEmpty()) {
                return new ArrayList<>();
            }
            // 浅拷贝 List 引用（内部对象仍为同一实例）
            List<T> copyList = new ArrayList<>(mainData);
            MergeStepImpl<T, K, V> copyBuilder = new MergeStepImpl<>(
                    copyList, keyExtractor, queryFunc, resultKeyExtractor
            );
            if (isOne) {
                copyBuilder.merge(oneMerger);
            } else {
                copyBuilder.mergeList(manyMerger);
            }
            return copyBuilder.execute();
        }
    }

    // ==================== 五、私有内部辅助方法 ====================

    /**
     * 单次批量查询并转 Map（内部实现，自带监控埋点）
     */
    private static <K, V> Map<K, V> doJoinMap(
            Collection<K> keys,
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
                        () -> new LinkedHashMap<>((int) (keys.size() / 0.75f) + 1)
                ));
    }

    /**
     * 将查询结果按 Key 分组为 Map&lt;K, List&lt;V&gt;&gt;
     */
    private static <K, V> Map<K, List<V>> doGroupByKey(
            List<V> resultList,
            Function<V, K> keyExtractor) {

        if (resultList == null || resultList.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return resultList.stream()
                .filter(Objects::nonNull)
                .filter(v -> keyExtractor.apply(v) != null)
                .collect(Collectors.groupingBy(
                        keyExtractor,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    /**
     * 执行查询并统一处理异常、耗时监控、空指针防护
     */
    private static <K, V> List<V> executeQuery(
            Collection<K> keys,
            Function<Collection<K>, List<V>> queryFunc) {

        long start = System.currentTimeMillis();
        try {
            List<V> result = queryFunc.apply(keys);
            long cost = System.currentTimeMillis() - start;
            if (cost > SLOW_QUERY_THRESHOLD_MS) {
                log.warn("InMemoryJoin slow query detected, keySize={}, costMs={}", keys.size(), cost);
            }
            if (log.isDebugEnabled()) {
                log.debug("InMemoryJoin batch query success, keySize={}, costMs={}", keys.size(), cost);
            }
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("InMemoryJoin query failed, keySize={}, costMs={}", keys.size(), cost, e);
            throw new RuntimeException("内存关联批量查询失败, keySize=" + keys.size(), e);
        }
    }

    /**
     * 内部提取 Key（复用优化逻辑，避免 Stream 依赖）
     */
    private static <T, K> Set<K> extractKeysInternal(List<T> list, Function<T, K> keyMapper) {
        if (list == null || list.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<K> result = new LinkedHashSet<>((int) (list.size() / 0.75f) + 1);
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
}
