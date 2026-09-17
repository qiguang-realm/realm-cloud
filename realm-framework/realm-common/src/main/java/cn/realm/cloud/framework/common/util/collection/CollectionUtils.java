package cn.realm.cloud.framework.common.util.collection;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ArrayUtil;
import cn.realm.cloud.framework.common.pojo.PageResult;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * 集合（Collection）工具类，提供集合的转换、过滤、分组、比较等常用操作。
 *
 * @author QI Guang
 */
public class CollectionUtils {

    private static final Logger log = LoggerFactory.getLogger(CollectionUtils.class);

    private CollectionUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    // ==================== 基础判断方法 ====================

    /**
     * 判断 source 是否包含任意一个 targets 中的元素。
     *
     * @param source  被检查的对象
     * @param targets 待匹配的对象数组
     * @return 如果 source 在 targets 中，返回 true；否则 false
     */
    public static boolean containsAny(Object source, Object... targets) {
        if (targets == null || targets.length == 0) {
            log.trace("containsAny: targets is null or empty, returning false");
            return false;
        }
        boolean result = asList(targets).contains(source);
        log.trace("containsAny: source={}, targets={}, result={}", source, Arrays.toString(targets), result);
        return result;
    }

    /**
     * 判断多个集合中是否存在至少一个空集合。
     *
     * @param collections 待检查的集合数组
     * @return 如果任意一个集合为 null 或空，返回 true；否则 false
     */
    public static boolean isAnyEmpty(Collection<?>... collections) {
        if (collections == null) {
            log.trace("isAnyEmpty: collections is null, returning true");
            return true;
        }
        boolean anyEmpty = Arrays.stream(collections).anyMatch(CollectionUtil::isEmpty);
        log.trace("isAnyEmpty: collections count={}, anyEmpty={}", collections.length, anyEmpty);
        return anyEmpty;
    }

    /**
     * 判断集合中是否存在至少一个元素满足条件。
     *
     * @param from      集合
     * @param predicate 条件
     * @param <T>       元素类型
     * @return 存在满足条件的元素返回 true，否则 false
     */
    public static <T> boolean anyMatch(Collection<T> from, Predicate<T> predicate) {
        if (CollUtil.isEmpty(from) || predicate == null) {
            log.trace("anyMatch: from empty or predicate null, returning false");
            return false;
        }
        boolean result = from.stream().anyMatch(predicate);
        log.trace("anyMatch: from size={}, result={}", from.size(), result);
        return result;
    }

    // ==================== 过滤与转换 ====================

    /**
     * 过滤集合，返回满足条件的元素列表。
     *
     * @param from      源集合
     * @param predicate 条件
     * @param <T>       元素类型
     * @return 满足条件的元素列表（非空，可能为空列表）
     */
    public static <T> List<T> filterList(Collection<T> from, Predicate<T> predicate) {
        if (CollUtil.isEmpty(from) || predicate == null) {
            log.trace("filterList: from empty or predicate null, returning empty list");
            return new ArrayList<>();
        }
        return from.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * 根据 key 去重，保留第一个出现的元素（默认）。
     *
     * @param from      源集合
     * @param keyMapper key 提取函数
     * @param <T>       元素类型
     * @param <R>       key 类型
     * @return 去重后的列表
     */
    public static <T, R> List<T> distinct(Collection<T> from, Function<T, R> keyMapper) {
        return distinct(from, keyMapper, (t1, t2) -> t1);
    }

    /**
     * 根据 key 去重，冲突时使用 cover 合并。
     *
     * @param from      源集合
     * @param keyMapper key 提取函数
     * @param cover     冲突时保留哪个元素（BinaryOperator）
     * @param <T>       元素类型
     * @param <R>       key 类型
     * @return 去重后的列表
     */
    public static <T, R> List<T> distinct(Collection<T> from, Function<T, R> keyMapper, BinaryOperator<T> cover) {
        if (CollUtil.isEmpty(from)) {
            log.trace("distinct: from empty, returning empty list");
            return new ArrayList<>();
        }
        Map<R, T> map = convertMap(from, keyMapper, Function.identity(), cover);
        List<T> result = new ArrayList<>(map.values());
        log.debug("distinct: from size={}, result size={}", from.size(), result.size());
        return result;
    }

    // ==================== 列表转换（List） ====================

    /**
     * 将数组转换为列表，通过映射函数转换元素类型。
     *
     * @param from 源数组
     * @param func 映射函数
     * @param <T>  源类型
     * @param <U>  目标类型
     * @return 转换后的列表
     */
    public static <T, U> List<U> convertList(T[] from, Function<T, U> func) {
        if (ArrayUtil.isEmpty(from)) {
            log.trace("convertList: from array empty, returning empty list");
            return new ArrayList<>();
        }
        return convertList(Arrays.asList(from), func);
    }

    /**
     * 将集合转换为列表，通过映射函数转换元素类型，并过滤掉 null。
     *
     * @param from 源集合
     * @param func 映射函数
     * @param <T>  源类型
     * @param <U>  目标类型
     * @return 转换后的列表
     */
    public static <T, U> List<U> convertList(Collection<T> from, Function<T, U> func) {
        if (CollUtil.isEmpty(from) || func == null) {
            log.trace("convertList: from empty or func null, returning empty list");
            return new ArrayList<>();
        }
        return from.stream().map(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 将集合转换为列表，先过滤再映射，并过滤掉 null。
     *
     * @param from   源集合
     * @param func   映射函数
     * @param filter 过滤条件
     * @param <T>    源类型
     * @param <U>    目标类型
     * @return 转换后的列表
     */
    public static <T, U> List<U> convertList(Collection<T> from, Function<T, U> func, Predicate<T> filter) {
        if (CollUtil.isEmpty(from) || func == null) {
            log.trace("convertList (with filter): from empty or func null, returning empty list");
            return new ArrayList<>();
        }
        Stream<T> stream = from.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        return stream.map(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 将分页结果转换为新类型的分页结果。
     *
     * @param from 源分页结果
     * @param func 元素映射函数
     * @param <T>  源类型
     * @param <U>  目标类型
     * @return 转换后的分页结果
     */
    public static <T, U> PageResult<U> convertPage(PageResult<T> from, Function<T, U> func) {
        if (from == null || CollUtil.isEmpty(from.getList())) {
            log.trace("convertPage: from null or list empty, returning page with same total");
            long total = from == null ? 0 : from.getTotal();
            return new PageResult<>(new ArrayList<>(), total);
        }
        List<U> list = convertList(from.getList(), func);
        return new PageResult<>(list, from.getTotal());
    }

    /**
     * 将集合通过 flatMap 转换为列表。
     *
     * @param from 源集合
     * @param func flatMap 函数
     * @param <T>  源类型
     * @param <U>  目标类型
     * @return 转换后的列表
     */
    public static <T, U> List<U> convertListByFlatMap(Collection<T> from,
                                                      Function<T, ? extends Stream<? extends U>> func) {
        if (CollUtil.isEmpty(from) || func == null) {
            log.trace("convertListByFlatMap: from empty or func null, returning empty list");
            return new ArrayList<>();
        }
        return from.stream().filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 先映射再 flatMap，将集合转换为列表。
     *
     * @param from   源集合
     * @param mapper 先映射
     * @param func   flatMap 函数
     * @param <T>    源类型
     * @param <U>    中间类型
     * @param <R>    目标类型
     * @return 转换后的列表
     */
    public static <T, U, R> List<R> convertListByFlatMap(Collection<T> from,
                                                         Function<? super T, ? extends U> mapper,
                                                         Function<U, ? extends Stream<? extends R>> func) {
        if (CollUtil.isEmpty(from) || mapper == null || func == null) {
            log.trace("convertListByFlatMap (2): from empty or func null, returning empty list");
            return new ArrayList<>();
        }
        return from.stream().map(mapper).filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 将 Map 的 values（列表）合并为一个列表。
     *
     * @param map Map<K, List<V>>
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 合并后的列表
     */
    public static <K, V> List<V> mergeValuesFromMap(Map<K, List<V>> map) {
        if (map == null || map.isEmpty()) {
            log.trace("mergeValuesFromMap: map empty, returning empty list");
            return new ArrayList<>();
        }
        return map.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    // ==================== Set 转换 ====================

    /**
     * 将集合转换为 Set。
     *
     * @param from 源集合
     * @param <T>  元素类型
     * @return Set 集合
     */
    public static <T> Set<T> convertSet(Collection<T> from) {
        return convertSet(from, v -> v);
    }

    /**
     * 将集合转换为 Set，通过映射函数转换元素。
     *
     * @param from 源集合
     * @param func 映射函数
     * @param <T>  源类型
     * @param <U>  目标类型
     * @return Set 集合
     */
    public static <T, U> Set<U> convertSet(Collection<T> from, Function<T, U> func) {
        if (CollUtil.isEmpty(from) || func == null) {
            log.trace("convertSet: from empty or func null, returning empty set");
            return new HashSet<>();
        }
        return from.stream().map(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 先过滤再映射，转换为 Set。
     *
     * @param from   源集合
     * @param func   映射函数
     * @param filter 过滤条件
     * @param <T>    源类型
     * @param <U>    目标类型
     * @return Set 集合
     */
    public static <T, U> Set<U> convertSet(Collection<T> from, Function<T, U> func, Predicate<T> filter) {
        if (CollUtil.isEmpty(from) || func == null) {
            log.trace("convertSet (with filter): from empty or func null, returning empty set");
            return new HashSet<>();
        }
        Stream<T> stream = from.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        return stream.map(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 通过 flatMap 转换为 Set。
     *
     * @param from 源集合
     * @param func flatMap 函数
     * @param <T>  源类型
     * @param <U>  目标类型
     * @return Set 集合
     */
    public static <T, U> Set<U> convertSetByFlatMap(Collection<T> from,
                                                    Function<T, ? extends Stream<? extends U>> func) {
        if (CollUtil.isEmpty(from) || func == null) {
            log.trace("convertSetByFlatMap: from empty or func null, returning empty set");
            return new HashSet<>();
        }
        return from.stream().filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 先映射再 flatMap，转换为 Set。
     *
     * @param from   源集合
     * @param mapper 先映射
     * @param func   flatMap 函数
     * @param <T>    源类型
     * @param <U>    中间类型
     * @param <R>    目标类型
     * @return Set 集合
     */
    public static <T, U, R> Set<R> convertSetByFlatMap(Collection<T> from,
                                                       Function<? super T, ? extends U> mapper,
                                                       Function<U, ? extends Stream<? extends R>> func) {
        if (CollUtil.isEmpty(from) || mapper == null || func == null) {
            log.trace("convertSetByFlatMap (2): from empty or func null, returning empty set");
            return new HashSet<>();
        }
        return from.stream().map(mapper).filter(Objects::nonNull).flatMap(func).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    // ==================== Map 转换（键值对） ====================

    /**
     * 将集合转换为 Map（键为 keyFunc 结果，值为原对象）。
     *
     * @param from    源集合
     * @param keyFunc 键提取函数
     * @param <T>     元素类型
     * @param <K>     键类型
     * @return Map
     */
    public static <T, K> Map<K, T> convertMap(Collection<T> from, Function<T, K> keyFunc) {
        return convertMap(from, keyFunc, Function.identity());
    }

    /**
     * 将集合转换为 Map，支持自定义 Map 实现。
     *
     * @param from     源集合
     * @param keyFunc  键提取函数
     * @param supplier Map 工厂
     * @param <T>      元素类型
     * @param <K>      键类型
     * @return Map
     */
    public static <T, K> Map<K, T> convertMap(Collection<T> from, Function<T, K> keyFunc, Supplier<? extends Map<K, T>> supplier) {
        return convertMap(from, keyFunc, Function.identity(), supplier);
    }

    /**
     * 将集合转换为 Map（键为 keyFunc，值为 valueFunc），冲突时保留第一个。
     *
     * @param from      源集合
     * @param keyFunc   键提取函数
     * @param valueFunc 值提取函数
     * @param <T>       元素类型
     * @param <K>       键类型
     * @param <V>       值类型
     * @return Map
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc) {
        return convertMap(from, keyFunc, valueFunc, (v1, v2) -> v1);
    }

    /**
     * 将集合转换为 Map，冲突时使用 mergeFunction 合并。
     *
     * @param from          源集合
     * @param keyFunc       键提取函数
     * @param valueFunc     值提取函数
     * @param mergeFunction 冲突合并函数
     * @param <T>           元素类型
     * @param <K>           键类型
     * @param <V>           值类型
     * @return Map
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc,
                                                 Function<T, V> valueFunc, BinaryOperator<V> mergeFunction) {
        return convertMap(from, keyFunc, valueFunc, mergeFunction, HashMap::new);
    }

    /**
     * 将集合转换为 Map，支持自定义 Map 实现和冲突合并策略。
     *
     * @param from      源集合
     * @param keyFunc   键提取函数
     * @param valueFunc 值提取函数
     * @param supplier  Map 工厂
     * @param <T>       元素类型
     * @param <K>       键类型
     * @param <V>       值类型
     * @return Map
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc,
                                                 Function<T, V> valueFunc, Supplier<? extends Map<K, V>> supplier) {
        return convertMap(from, keyFunc, valueFunc, (v1, v2) -> v1, supplier);
    }

    /**
     * 将集合转换为 Map，完整参数版本。
     *
     * @param from          源集合
     * @param keyFunc       键提取函数
     * @param valueFunc     值提取函数
     * @param mergeFunction 冲突合并函数
     * @param supplier      Map 工厂
     * @param <T>           元素类型
     * @param <K>           键类型
     * @param <V>           值类型
     * @return Map
     */
    public static <T, K, V> Map<K, V> convertMap(Collection<T> from, Function<T, K> keyFunc,
                                                 Function<T, V> valueFunc, BinaryOperator<V> mergeFunction,
                                                 Supplier<? extends Map<K, V>> supplier) {
        if (CollUtil.isEmpty(from)) {
            log.trace("convertMap: from empty, returning empty map from supplier");
            return supplier.get();
        }
        Map<K, V> map = from.stream().collect(Collectors.toMap(keyFunc, valueFunc, mergeFunction, supplier));
        log.debug("convertMap: from size={}, map size={}", from.size(), map.size());
        return map;
    }

    /**
     * 将集合转换为 MultiMap（键->List），值为原对象。
     *
     * @param from    源集合
     * @param keyFunc 键提取函数
     * @param <T>     元素类型
     * @param <K>     键类型
     * @return Map<K, List < T>>
     */
    public static <T, K> Map<K, List<T>> convertMultiMap(Collection<T> from, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            log.trace("convertMultiMap: from empty, returning empty map");
            return new HashMap<>();
        }
        return from.stream().collect(Collectors.groupingBy(keyFunc, Collectors.mapping(t -> t, Collectors.toList())));
    }

    /**
     * 将集合转换为 MultiMap（键->List），值为映射后的值。
     *
     * @param from      源集合
     * @param keyFunc   键提取函数
     * @param valueFunc 值提取函数
     * @param <T>       元素类型
     * @param <K>       键类型
     * @param <V>       值类型
     * @return Map<K, List < V>>
     */
    public static <T, K, V> Map<K, List<V>> convertMultiMap(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            log.trace("convertMultiMap (with valueFunc): from empty, returning empty map");
            return new HashMap<>();
        }
        return from.stream().collect(Collectors.groupingBy(keyFunc, Collectors.mapping(valueFunc, Collectors.toList())));
    }

    /**
     * 将集合转换为 MultiMap（键->Set），值为映射后的值。
     *
     * @param from      源集合
     * @param keyFunc   键提取函数
     * @param valueFunc 值提取函数
     * @param <T>       元素类型
     * @param <K>       键类型
     * @param <V>       值类型
     * @return Map<K, Set < V>>
     */
    public static <T, K, V> Map<K, Set<V>> convertMultiMap2(Collection<T> from, Function<T, K> keyFunc, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from)) {
            log.trace("convertMultiMap2: from empty, returning empty map");
            return new HashMap<>();
        }
        return from.stream().collect(Collectors.groupingBy(keyFunc, Collectors.mapping(valueFunc, Collectors.toSet())));
    }

    /**
     * 将集合转换为不可变的 ImmutableMap（键为 keyFunc，值为原对象）。
     *
     * @param from    源集合
     * @param keyFunc 键提取函数
     * @param <T>     元素类型
     * @param <K>     键类型
     * @return ImmutableMap
     */
    public static <T, K> Map<K, T> convertImmutableMap(Collection<T> from, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            log.trace("convertImmutableMap: from empty, returning empty map");
            return Collections.emptyMap();
        }
        ImmutableMap.Builder<K, T> builder = ImmutableMap.builder();
        for (T item : from) {
            builder.put(keyFunc.apply(item), item);
        }
        Map<K, T> map = builder.build();
        log.debug("convertImmutableMap: from size={}, map size={}", from.size(), map.size());
        return map;
    }

    /**
     * 将集合转换为 Map，支持过滤条件。
     *
     * @param from    源集合
     * @param filter  过滤条件
     * @param keyFunc 键提取函数
     * @param <T>     元素类型
     * @param <K>     键类型
     * @return Map
     */
    public static <T, K> Map<K, T> convertMapByFilter(Collection<T> from, Predicate<T> filter, Function<T, K> keyFunc) {
        if (CollUtil.isEmpty(from)) {
            log.trace("convertMapByFilter: from empty, returning empty map");
            return new HashMap<>();
        }
        Stream<T> stream = from.stream();
        if (filter != null) {
            stream = stream.filter(filter);
        }
        Map<K, T> map = stream.collect(Collectors.toMap(keyFunc, Function.identity(), (v1, v2) -> v1));
        log.debug("convertMapByFilter: from size={}, map size={}", from.size(), map.size());
        return map;
    }

    // ==================== 列表对比 ====================

    /**
     * 对比新旧两个列表，找出新增、修改、删除的数据。
     *
     * <p><b>注意：</b>该方法时间复杂度为 O(n*m)，适用于小数据量场景。
     *
     * @param oldList  旧列表
     * @param newList  新列表
     * @param sameFunc 判断两个对象是否相同的函数（通常基于业务主键）
     * @param <T>      元素类型
     * @return 列表数组：[新增列表, 修改列表, 删除列表]
     */
    public static <T> List<List<T>> diffList(Collection<T> oldList, Collection<T> newList,
                                             BiFunction<T, T, Boolean> sameFunc) {
        // 初始化结果容器
        List<T> createList = new LinkedList<>(newList); // 默认所有新元素都是新增
        List<T> updateList = new ArrayList<>();
        List<T> deleteList = new ArrayList<>();

        // 遍历旧列表，确定修改和删除
        for (T oldObj : oldList) {
            T foundObj = null;
            Iterator<T> iterator = createList.iterator();
            while (iterator.hasNext()) {
                T newObj = iterator.next();
                if (sameFunc.apply(oldObj, newObj)) {
                    foundObj = newObj;
                    iterator.remove(); // 匹配到，从新增列表中移除
                    break;
                }
            }
            if (foundObj != null) {
                updateList.add(foundObj);
            } else {
                deleteList.add(oldObj);
            }
        }
        log.debug("diffList: oldSize={}, newSize={}, create={}, update={}, delete={}",
                oldList == null ? 0 : oldList.size(),
                newList == null ? 0 : newList.size(),
                createList.size(), updateList.size(), deleteList.size());
        return asList(createList, updateList, deleteList);
    }

    // ==================== 集合包含判断 ====================

    /**
     * 判断 source 集合是否包含 candidates 中的任意元素。
     *
     * @param source     源集合
     * @param candidates 候选集合
     * @return 包含任意元素返回 true
     */
    public static boolean containsAny(Collection<?> source, Collection<?> candidates) {
        if (source == null || candidates == null) {
            log.trace("containsAny: source or candidates null, returning false");
            return false;
        }
        boolean result = org.springframework.util.CollectionUtils.containsAny(source, candidates);
        log.trace("containsAny: source size={}, candidates size={}, result={}",
                source.size(), candidates.size(), result);
        return result;
    }

    // ==================== 获取元素 ====================

    /**
     * 安全获取列表的第一个元素。
     *
     * @param from 源列表
     * @param <T>  元素类型
     * @return 第一个元素，若列表为空则返回 null
     */
    public static <T> T getFirst(List<T> from) {
        if (CollectionUtil.isEmpty(from)) {
            log.trace("getFirst: list empty, returning null");
            return null;
        }
        return from.get(0);
    }

    /**
     * 查找满足条件的第一个元素。
     *
     * @param from      源集合
     * @param predicate 条件
     * @param <T>       元素类型
     * @return 满足条件的元素，不存在则返回 null
     */
    public static <T> T findFirst(Collection<T> from, Predicate<T> predicate) {
        return findFirst(from, predicate, Function.identity());
    }

    /**
     * 查找满足条件的第一个元素，并应用映射函数。
     *
     * @param from      源集合
     * @param predicate 条件
     * @param func      映射函数
     * @param <T>       源类型
     * @param <U>       目标类型
     * @return 映射后的结果，不存在则返回 null
     */
    public static <T, U> U findFirst(Collection<T> from, Predicate<T> predicate, Function<T, U> func) {
        if (CollUtil.isEmpty(from) || predicate == null || func == null) {
            log.trace("findFirst: from empty or predicate/func null, returning null");
            return null;
        }
        return from.stream().filter(predicate).findFirst().map(func).orElse(null);
    }

    // ==================== 聚合计算 ====================

    /**
     * 获取集合中指定字段的最大值。
     *
     * @param from      源集合
     * @param valueFunc 值提取函数
     * @param <T>       元素类型
     * @param <V>       值类型（必须实现 Comparable）
     * @return 最大值，若集合为空则返回 null
     */
    public static <T, V extends Comparable<? super V>> V getMaxValue(Collection<T> from, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from) || valueFunc == null) {
            log.trace("getMaxValue: from empty or valueFunc null, returning null");
            return null;
        }
        return from.stream().map(valueFunc).filter(Objects::nonNull).max(Comparable::compareTo).orElse(null);
    }

    /**
     * 获取集合中指定字段的最小值。
     *
     * @param from      源集合
     * @param valueFunc 值提取函数
     * @param <T>       元素类型
     * @param <V>       值类型
     * @return 最小值，若集合为空则返回 null
     */
    public static <T, V extends Comparable<? super V>> V getMinValue(List<T> from, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from) || valueFunc == null) {
            log.trace("getMinValue: from empty or valueFunc null, returning null");
            return null;
        }
        return from.stream().map(valueFunc).filter(Objects::nonNull).min(Comparable::compareTo).orElse(null);
    }

    /**
     * 获取集合中指定字段值最小的对象。
     *
     * @param from      源集合
     * @param valueFunc 值提取函数
     * @param <T>       元素类型
     * @param <V>       值类型
     * @return 对应元素，若集合为空则返回 null
     */
    public static <T, V extends Comparable<? super V>> T getMinObject(List<T> from, Function<T, V> valueFunc) {
        if (CollUtil.isEmpty(from) || valueFunc == null) {
            log.trace("getMinObject: from empty or valueFunc null, returning null");
            return null;
        }
        return from.stream().min(Comparator.comparing(valueFunc, Comparator.nullsLast(Comparable::compareTo))).orElse(null);
    }

    /**
     * 对集合中指定字段进行累加（无默认值）。
     *
     * @param from        源集合
     * @param valueFunc   值提取函数
     * @param accumulator 累加函数
     * @param <T>         元素类型
     * @param <V>         值类型
     * @return 累加结果，若集合为空则返回 null
     */
    public static <T, V extends Comparable<? super V>> V getSumValue(Collection<T> from, Function<T, V> valueFunc,
                                                                     BinaryOperator<V> accumulator) {
        return getSumValue(from, valueFunc, accumulator, null);
    }

    /**
     * 对集合中指定字段进行累加，支持默认值。
     *
     * @param from         源集合
     * @param valueFunc    值提取函数
     * @param accumulator  累加函数
     * @param defaultValue 默认值（集合为空时返回）
     * @param <T>          元素类型
     * @param <V>          值类型
     * @return 累加结果
     */
    public static <T, V extends Comparable<? super V>> V getSumValue(Collection<T> from, Function<T, V> valueFunc,
                                                                     BinaryOperator<V> accumulator, V defaultValue) {
        if (CollUtil.isEmpty(from) || valueFunc == null || accumulator == null) {
            log.trace("getSumValue: from empty or valueFunc/accumulator null, returning defaultValue={}", defaultValue);
            return defaultValue;
        }
        return from.stream().map(valueFunc).filter(Objects::nonNull).reduce(accumulator).orElse(defaultValue);
    }

    // ==================== 添加元素 ====================

    /**
     * 如果元素不为 null，则添加到集合中。
     *
     * @param coll 集合
     * @param item 元素
     * @param <T>  元素类型
     */
    public static <T> void addIfNotNull(Collection<T> coll, T item) {
        if (coll == null) {
            log.warn("addIfNotNull: coll is null, cannot add item");
            return;
        }
        if (item != null) {
            coll.add(item);
        } else {
            log.trace("addIfNotNull: item is null, skip adding");
        }
    }

    /**
     * 将对象包装为单元素集合（若对象为 null 则返回空集合）。
     *
     * @param obj 对象
     * @param <T> 元素类型
     * @return 单元素集合（不可变）
     */
    public static <T> Collection<T> singleton(T obj) {
        if (obj == null) {
            log.trace("singleton: obj is null, returning empty list");
            return Collections.emptyList();
        }
        return Collections.singleton(obj);
    }

    /**
     * 将多层嵌套的列表扁平化为单层列表。
     *
     * @param list 列表的列表
     * @param <T>  元素类型
     * @return 扁平化后的列表
     */
    public static <T> List<T> newArrayList(List<List<T>> list) {
        if (CollUtil.isEmpty(list)) {
            log.trace("newArrayList: list empty, returning empty list");
            return new ArrayList<>();
        }
        return list.stream().filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toList());
    }

    /**
     * 将任意值转换为 LinkedHashSet（使用 Hutool 的 Convert 工具）。
     *
     * @param elementType 集合元素类型
     * @param value       被转换的值
     * @param <T>         元素类型
     * @return LinkedHashSet
     */
    @SuppressWarnings("unchecked")
    public static <T> LinkedHashSet<T> toLinkedHashSet(Class<T> elementType, Object value) {
        if (elementType == null) {
            log.warn("toLinkedHashSet: elementType is null, returning empty set");
            return new LinkedHashSet<>();
        }
        try {
            LinkedHashSet<T> result = (LinkedHashSet<T>) Convert.toCollection(LinkedHashSet.class, elementType, value);
            log.trace("toLinkedHashSet: elementType={}, value type={}, result size={}",
                    elementType.getName(), value == null ? "null" : value.getClass().getSimpleName(),
                    result == null ? 0 : result.size());
            return result;
        } catch (Exception e) {
            log.warn("toLinkedHashSet: conversion failed for elementType={}, value={}", elementType, value, e);
            return new LinkedHashSet<>();
        }
    }
}
