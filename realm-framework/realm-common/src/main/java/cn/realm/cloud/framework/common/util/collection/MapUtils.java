package cn.realm.cloud.framework.common.util.collection;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.realm.cloud.framework.common.core.KeyValue;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/**
 * Map 工具类，提供常用的 Map 操作增强方法。
 *
 * @author QI Guang
 */
public class MapUtils {

    private static final Logger log = LoggerFactory.getLogger(MapUtils.class);

    private MapUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 从 {@link Multimap} 中获取指定键集合对应的所有值列表。
     *
     * <p>该方法会遍历 keys 中的每个键，从 multimap 中获取对应的值集合（可能为空），
     * 然后将所有非空值集合合并为一个列表返回。
     *
     * @param multimap 哈希表（Multimap 结构）
     * @param keys     键集合
     * @param <K>      键类型
     * @param <V>      值类型
     * @return 包含所有匹配值的列表（可能为空），不会返回 null
     */
    public static <K, V> List<V> getList(Multimap<K, V> multimap, Collection<K> keys) {
        if (multimap == null) {
            log.debug("getList called with null multimap, returning empty list");
            return Collections.emptyList();
        }
        if (CollectionUtil.isEmpty(keys)) {
            log.trace("getList called with empty keys, returning empty list");
            return Collections.emptyList();
        }

        // 预估容量，避免多次扩容（若 keys 较大时有效）
        List<V> result = new ArrayList<>(keys.size());
        for (K key : keys) {
            Collection<V> values = multimap.get(key);
            if (CollectionUtil.isEmpty(values)) {
                log.trace("No values found for key: {}", key);
                continue;
            }
            result.addAll(values);
        }
        log.debug("getList retrieved {} values for {} keys", result.size(), keys.size());
        return result;
    }

    /**
     * 从 Map 中查找指定键对应的值，并在值非空时执行自定义操作。
     *
     * <p>如果键为 null、Map 为空、或查找到的值为 null，则直接返回，不执行 consumer。
     *
     * @param map      哈希表
     * @param key      键
     * @param consumer 对值的处理逻辑
     * @param <K>      键类型
     * @param <V>      值类型
     */
    public static <K, V> void findAndThen(Map<K, V> map, K key, Consumer<V> consumer) {
        if (consumer == null) {
            log.warn("findAndThen called with null consumer, ignoring");
            return;
        }
        if (ObjUtil.isNull(key)) {
            log.trace("findAndThen called with null key, ignoring");
            return;
        }
        if (CollUtil.isEmpty(map)) {
            log.trace("findAndThen called with empty map, ignoring");
            return;
        }

        V value = map.get(key);
        if (value == null) {
            log.trace("findAndThen: no value found for key: {}", key);
            return;
        }

        try {
            consumer.accept(value);
        } catch (Exception e) {
            log.error("findAndThen: consumer execution failed for key: {}", key, e);
        }
    }

    /**
     * 将键值对列表转换为 LinkedHashMap（保持插入顺序）。
     *
     * <p>如果输入列表为 null 或空，返回空 Map。
     *
     * @param keyValues 键值对列表
     * @param <K>       键类型
     * @param <V>       值类型
     * @return 转换后的 Map（LinkedHashMap 实现）
     */
    public static <K, V> Map<K, V> convertMap(List<KeyValue<K, V>> keyValues) {
        if (CollectionUtil.isEmpty(keyValues)) {
            log.debug("convertMap called with empty or null list, returning empty map");
            return Collections.emptyMap();
        }

        // 使用 LinkedHashMap 保持顺序，并预分配容量
        Map<K, V> map = Maps.newLinkedHashMapWithExpectedSize(keyValues.size());
        for (KeyValue<K, V> kv : keyValues) {
            if (kv == null) {
                log.warn("convertMap encountered null KeyValue, skipping");
                continue;
            }
            K key = kv.getKey();
            V value = kv.getValue();
            if (map.containsKey(key)) {
                log.debug("convertMap: duplicate key '{}', overwriting previous value", key);
            }
            map.put(key, value);
        }
        log.debug("convertMap converted {} entries", map.size());
        return map;
    }

    /**
     * 从 Map 中获取指定键对应的 BigDecimal 值。
     *
     * <p>支持值类型为 BigDecimal、Number（如 Integer、Long）、String（可解析为数字）。
     * 若值为 null 或类型不匹配且无法转换，则返回默认值 null。
     *
     * @param map Map 数据源
     * @param key 键名
     * @return BigDecimal 值，解析失败或不存在时返回 null
     */
    public static BigDecimal getBigDecimal(Map<String, ?> map, String key) {
        return getBigDecimal(map, key, null);
    }

    /**
     * 从 Map 中获取指定键对应的 BigDecimal 值，支持默认值。
     *
     * <p>支持值类型：BigDecimal、Number、String（可解析为数字）。
     * 若值为 null 或类型不匹配且无法转换，则返回默认值。
     *
     * @param map          Map 数据源
     * @param key          键名
     * @param defaultValue 默认值（可为 null）
     * @return BigDecimal 值，解析失败或不存在时返回默认值
     */
    public static BigDecimal getBigDecimal(Map<String, ?> map, String key, BigDecimal defaultValue) {
        if (map == null) {
            log.trace("getBigDecimal called with null map, returning defaultValue: {}", defaultValue);
            return defaultValue;
        }
        if (key == null) {
            log.trace("getBigDecimal called with null key, returning defaultValue: {}", defaultValue);
            return defaultValue;
        }

        Object value = map.get(key);
        if (value == null) {
            log.trace("getBigDecimal: key '{}' not found or value is null, returning defaultValue: {}", key, defaultValue);
            return defaultValue;
        }

        // 直接返回 BigDecimal
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        // 数字类型转换
        if (value instanceof Number) {
            try {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            } catch (NumberFormatException e) {
                log.warn("getBigDecimal: failed to convert Number '{}' to BigDecimal, returning defaultValue: {}", value, defaultValue);
                return defaultValue;
            }
        }
        // 字符串解析
        if (value instanceof String) {
            String str = (String) value;
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                log.warn("getBigDecimal: failed to parse String '{}' to BigDecimal, returning defaultValue: {}", str, defaultValue);
                return defaultValue;
            }
        }
        // 不支持的类型
        log.warn("getBigDecimal: unsupported value type '{}' for key '{}', returning defaultValue: {}", value.getClass().getSimpleName(), key, defaultValue);
        return defaultValue;
    }

    /**
     * 函数式接口 Consumer 的简单别名，用于 findAndThen 方法。
     * 此处使用 java.util.function.Consumer 即可，无需自定义。
     */
}
