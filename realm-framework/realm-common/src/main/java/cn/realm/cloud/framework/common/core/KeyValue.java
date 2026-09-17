package cn.realm.cloud.framework.common.core;

import java.io.Serializable;
import java.util.Objects;

/**
 * 键值对（Key-Value）对象，用于存储一对关联的键和值。
 *
 * <p>该类是不可变的，所有字段均为 {@code final}，线程安全。
 * 提供静态工厂方法 {@link #of(Object, Object)} 创建实例，同时也提供无参构造器以支持序列化框架（如 Jackson）。
 *
 * @param <K> 键的类型
 * @param <V> 值的类型
 * @author QI Guang
 */
public class KeyValue<K, V> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final K key;
    private final V value;

    /**
     * 无参构造器（供序列化框架使用，不推荐业务代码直接调用）。
     * 为 final 字段赋予默认值 null。
     */
    public KeyValue() {
        this.key = null;
        this.value = null;
    }

    /**
     * 全参构造器，创建包含指定键和值的 KeyValue 实例。
     *
     * @param key   键（可为 null）
     * @param value 值（可为 null）
     */
    public KeyValue(K key, V value) {
        this.key = key;
        this.value = value;
    }

    /**
     * 静态工厂方法，创建 KeyValue 实例。
     *
     * @param key   键
     * @param value 值
     * @param <K>   键类型
     * @param <V>   值类型
     * @return KeyValue 实例
     */
    public static <K, V> KeyValue<K, V> of(K key, V value) {
        return new KeyValue<>(key, value);
    }

    // ==================== Getters ====================

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    // ==================== Object 方法重写 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyValue<?, ?> that = (KeyValue<?, ?>) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "KeyValue{" +
                "key=" + key +
                ", value=" + value +
                '}';
    }
}
