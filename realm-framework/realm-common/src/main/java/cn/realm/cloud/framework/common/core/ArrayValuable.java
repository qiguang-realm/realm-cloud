package cn.realm.cloud.framework.common.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 可生成 T 类型数组的接口。
 *
 * <p>实现该接口的类应能返回一个类型为 T[] 的数组，通常用于枚举、常量集合等场景。
 *
 * <p><b>实现建议：</b>
 * <ul>
 *   <li>优先返回不可变数组，避免外部修改破坏内部状态。</li>
 *   <li>避免返回 {@code null}，推荐使用空数组（长度为 0）表示无元素。</li>
 *   <li>若数组内容可能变动，请返回副本或使用不可变集合包装。</li>
 * </ul>
 *
 * @param <T> 数组元素类型
 * @author QI Guang
 */
public interface ArrayValuable<T> {

    /**
     * 返回 T 类型的数组。
     *
     * <p><b>注意：</b>实现类应保证返回的数组不为 {@code null}，若无元素应返回长度为 0 的数组。
     *
     * @return T 类型的数组（非 null）
     */
    T[] array();

    // ==================== 默认方法 ====================

    /**
     * 获取数组的流式安全版本，返回一个 {@link Optional}，当数组为 {@code null} 时返回空 Optional。
     *
     * <p>此方法可避免直接调用 {@link #array()} 可能产生的空指针异常。
     *
     * @return 包装了数组的 Optional
     */
    default Optional<T[]> safeArray() {
        return Optional.ofNullable(array());
    }

    /**
     * 获取数组，若为 {@code null} 则返回一个长度为 0 的空数组。
     *
     * <p>推荐在业务代码中使用此方法，避免 NPE。
     *
     * @return 非空数组（可能长度为 0）
     */
    @SuppressWarnings("unchecked")
    default T[] arrayOrEmpty() {
        T[] arr = array();
        if (arr == null) {
            // 使用反射创建空数组，类型安全（需要知道元素类型）
            // 由于泛型擦除，这里使用默认 Object[] 并强转，实际调用时需确保类型正确。
            // 更好的实现需要子类提供类型，但接口无法做到，因此此处保守返回空 Object[]。
            // 实际使用时建议实现类重写此方法。
            return (T[]) new Object[0];
        }
        return arr;
    }

    // ==================== 静态工具方法 ====================

    /**
     * 安全地获取实现类的数组，若为 {@code null} 则返回指定默认数组。
     *
     * @param valuable     实现了 ArrayValuable 的对象
     * @param defaultValue 默认数组（可为 null）
     * @param <T>          元素类型
     * @return 非 null 数组（如果 valuable 为 null 或 array() 返回 null 则返回 defaultValue）
     */
    static <T> T[] safeArray(ArrayValuable<T> valuable, T[] defaultValue) {
        if (valuable == null) {
            return defaultValue;
        }
        T[] arr = valuable.array();
        return arr == null ? defaultValue : arr;
    }

    /**
     * 安全地获取实现类的数组，若为 {@code null} 则返回空数组。
     *
     * @param valuable 实现了 ArrayValuable 的对象
     * @param <T>      元素类型
     * @return 非空数组（可能长度为 0）
     */
    @SuppressWarnings("unchecked")
    static <T> T[] safeArrayOrEmpty(ArrayValuable<T> valuable) {
        if (valuable == null) {
            return (T[]) new Object[0];
        }
        T[] arr = valuable.array();
        return arr == null ? (T[]) new Object[0] : arr;
    }

    /**
     * 将实现类的数组转换为不可变列表（如果数组可能被修改，建议使用此方法返回副本）。
     *
     * @param valuable 实现了 ArrayValuable 的对象
     * @param <T>      元素类型
     * @return 不可变列表（若数组为 null 则返回空列表）
     */
    static <T> List<T> asList(ArrayValuable<T> valuable) {
        T[] arr = safeArrayOrEmpty(valuable);
        return Collections.unmodifiableList(Arrays.asList(arr));
    }
}
