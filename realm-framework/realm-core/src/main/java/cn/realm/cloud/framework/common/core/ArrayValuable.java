package cn.realm.cloud.framework.common.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * 可提供 {@code T[]} 数组的接口，常用于枚举、常量集合等场景。
 *
 * <p><b>实现契约（强制）：</b>
 * <ol>
 *     <li>{@link #array()} <b>必须返回非 {@code null} 数组</b>；
 *         无元素时返回长度为 0 的数组（{@code new T[0]}）。</li>
 *     <li>若内部维护可变数组，应返回<b>副本</b>或使用不可变集合，避免外部修改破坏内部状态。</li>
 *     <li>实现应为无副作用方法，可被多次调用。</li>
 * </ol>
 *
 * <p><b>类型安全说明：</b>
 * 由于 Java 泛型擦除，接口层无法安全创建 {@code T[]}。
 * 因此本接口不提供"自动返回空数组"的方法（会引发 {@link ClassCastException}），
 * 需要创建空数组时请使用 {@link #arrayOrEmpty(ArrayValuable, IntFunction)} 并显式提供类型。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 场景 1：获取数组（实现类保证非空）
 * String[] values = myEnum.array();
 *
 * // 场景 2：安全获取（valuable 可能为 null 或违反契约）
 * String[] safe = ArrayValuable.arrayOrElse(myEnum, new String[0]);
 *
 * // 场景 3：类型安全的空数组兜底
 * String[] safe2 = ArrayValuable.arrayOrEmpty(myEnum, String[]::new);
 *
 * // 场景 4：转换为不可变列表
 * List<String> list = ArrayValuable.toList(myEnum);
 * }</pre>
 *
 * @param <T> 数组元素类型
 * @author QI Guang
 */
public interface ArrayValuable<T> {

    // ==================== 抽象方法 ====================

    /**
     * 返回 {@code T} 类型的数组。
     *
     * <p><b>契约：</b>实现类<b>必须</b>返回非 {@code null} 数组；
     * 无元素时返回长度为 0 的数组。
     *
     * @return {@code T} 类型数组，永不为 {@code null}
     */
    T[] array();

    // ==================== 静态工具方法 ====================

    /**
     * 安全获取数组：若 {@code valuable} 为 {@code null} 或其 {@link #array()} 返回 {@code null}，
     * 则返回指定的默认数组。
     *
     * @param valuable     实现类实例，允许为 {@code null}
     * @param defaultValue 兜底数组，允许为 {@code null}
     * @param <T>          元素类型
     * @return 非 {@code null} 数组（除非 {@code defaultValue} 也为 {@code null}）
     */
    static <T> T[] arrayOrElse(ArrayValuable<T> valuable, T[] defaultValue) {
        if (valuable == null) {
            return defaultValue;
        }
        T[] arr = valuable.array();
        return arr != null ? arr : defaultValue;
    }

    /**
     * 安全获取数组：若 {@code valuable} 为 {@code null} 或其 {@link #array()} 返回 {@code null}，
     * 则通过 {@code generator} 创建长度为 0 的空数组。
     *
     * <p><b>类型安全：</b>{@code generator} 显式提供数组组件类型，
     * 避免泛型擦除导致的 {@link ClassCastException}。
     *
     * @param valuable  实现类实例，允许为 {@code null}
     * @param generator 数组生成器（如 {@code String[]::new}），不允许为 {@code null}
     * @param <T>       元素类型
     * @return 非 {@code null} 数组（可能长度为 0）
     */
    static <T> T[] arrayOrEmpty(ArrayValuable<T> valuable, IntFunction<T[]> generator) {
        Objects.requireNonNull(generator, "generator must not be null");
        T[] arr = valuable != null ? valuable.array() : null;
        return arr != null ? arr : generator.apply(0);
    }

    /**
     * 将实现类的数组转换为<b>不可变列表</b>。
     *
     * <p><b>实现细节：</b>使用 {@link List#of(Object[])} 创建不可变副本，
     * 而非 {@link Arrays#asList} 视图。因此对原数组的后续修改<b>不会</b>反映到返回列表中。
     *
     * @param valuable 实现类实例，允许为 {@code null}
     * @param <T>      元素类型
     * @return 不可变列表；{@code valuable} 为 {@code null} 或数组为空时返回空列表
     */
    static <T> List<T> toList(ArrayValuable<T> valuable) {
        if (valuable == null) {
            return Collections.emptyList();
        }
        T[] arr = valuable.array();
        if (arr == null || arr.length == 0) {
            return Collections.emptyList();
        }
        return List.of(arr);
    }
}
