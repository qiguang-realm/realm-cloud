package cn.realm.cloud.framework.common.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 不可变键值对，用于存储一对关联的键与值。
 *
 * <p>本类为 {@code final}，所有字段为 {@code final}，线程安全。
 * 反序列化通过 {@link #KeyValue(Object, Object)} 全参构造器完成
 * （Jackson 2.12+ 支持无参构造器缺失场景下的多参构造器绑定，无需无参构造器）。
 *
 * <p><b>命名说明：</b>本类与 JDK 的 {@link java.util.Map.Entry} 语义相近但用途不同：
 * {@code Map.Entry} 是 Map 的视图，生命周期与 Map 绑定；
 * {@code KeyValue} 是独立的值对象，可自由传递、存储、序列化。
 *
 * @param <K> 键的类型
 * @param <V> 值的类型
 * @author QI Guang
 */
public final class KeyValue<K, V> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final K key;
    private final V value;

    // ==================== 构造器 ====================

    /**
     * 全参构造器。
     *
     * <p><b>注意：</b>本类不提供无参构造器，因为：
     * <ol>
     *     <li>无参构造器会为 {@code final} 字段赋 {@code null}，产生"半成品"对象；</li>
     *     <li>Jackson 2.12+ 支持通过多参构造器反序列化，无需无参构造器。</li>
     * </ol>
     *
     * @param key   键（可为 {@code null}）
     * @param value 值（可为 {@code null}）
     */
    public KeyValue(K key, V value) {
        this.key = key;
        this.value = value;
    }

    // ==================== 静态工厂 ====================

    /**
     * 创建 {@link KeyValue} 实例。
     */
    public static <K, V> KeyValue<K, V> of(K key, V value) {
        return new KeyValue<>(key, value);
    }

    // ==================== 访问器 ====================

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    // ==================== Object 方法 ====================

    /**
     * {@inheritDoc}
     *
     * <p>使用 {@code instanceof} 判断类型（而非 {@code getClass()}），
     * 以保证 Liskov 替换原则。由于本类为 {@code final}，此处理论上无子类，
     * 但仍保持规范写法，便于未来扩展。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KeyValue<?, ?> that)) {
            return false;
        }
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "KeyValue{key=" + key + ", value=" + value + '}';
    }
}
