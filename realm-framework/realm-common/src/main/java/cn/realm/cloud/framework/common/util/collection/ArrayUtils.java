package cn.realm.cloud.framework.common.util.collection;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.IterUtil;
import cn.hutool.core.util.ArrayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数组工具类，提供数组的合并、转换和安全访问等常用操作。
 *
 * @author QI Guang
 */
public class ArrayUtils {

    private static final Logger log = LoggerFactory.getLogger(ArrayUtils.class);

    private ArrayUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 将单个对象与对象数组合并成一个新数组。
     *
     * <p>如果传入的单个对象为 {@code null}，则直接返回原数组（可能为 {@code null}）。
     * 如果原数组为 {@code null}，则返回仅包含单个对象的新数组。
     *
     * @param object      单个对象（可为 {@code null}）
     * @param newElements 对象数组（可为 {@code null}）
     * @param <T>         数组元素类型
     * @return 合并后的数组，若两个参数都为 {@code null} 则返回 {@code null}
     */
    @SafeVarargs
    public static <T> Consumer<T>[] append(Consumer<T> object, Consumer<T>... newElements) {
        if (object == null) {
            if (log.isTraceEnabled()) {
                log.trace("append: object is null, returning newElements array (may be null)");
            }
            return newElements;
        }
        if (newElements == null || newElements.length == 0) {
            if (log.isTraceEnabled()) {
                log.trace("append: newElements is null or empty, returning array containing only object");
            }
            @SuppressWarnings("unchecked")
            Consumer<T>[] result = (Consumer<T>[]) new Consumer<?>[1];
            result[0] = object;
            return result;
        }
        Consumer<T>[] result = ArrayUtil.newArray(Consumer.class, 1 + newElements.length);
        result[0] = object;
        System.arraycopy(newElements, 0, result, 1, newElements.length);
        if (log.isDebugEnabled()) {
            log.debug("append: merged {} new elements with object, result length: {}", newElements.length, result.length);
        }
        return result;
    }

    /**
     * 将集合转换为数组，通过映射函数转换元素类型。
     *
     * <p>如果源集合为空，返回空数组。
     * 映射函数不能为 {@code null}。
     *
     * @param from   源集合
     * @param mapper 元素映射函数
     * @param <T>    源类型
     * @param <V>    目标类型
     * @return 目标类型数组
     */
    @SuppressWarnings("unchecked")
    public static <T, V> V[] toArray(Collection<T> from, Function<T, V> mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        if (CollectionUtil.isEmpty(from)) {
            if (log.isTraceEnabled()) {
                log.trace("toArray (with mapper): source collection is empty, returning empty array");
            }
            return (V[]) new Object[0];
        }
        // 使用 Stream 进行转换，避免依赖外部工具类
        return from.stream()
                .map(mapper)
                .collect(Collectors.toList())
                .toArray((V[]) new Object[0]); // 注意：此处只能返回 Object[]，调用者需确保类型安全
    }

    /**
     * 将集合转换为同类型数组。
     *
     * <p>如果集合为空，返回空数组。
     * 使用 Hutool 的 {@link ArrayUtil#toArray(Collection, Class)} 方法，内部会尝试获取元素类型。
     *
     * @param from 源集合
     * @param <T>  元素类型
     * @return 数组
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Collection<T> from) {
        if (CollectionUtil.isEmpty(from)) {
            if (log.isTraceEnabled()) {
                log.trace("toArray: source collection is empty, returning empty array");
            }
            return (T[]) new Object[0];
        }
        try {
            // 使用 Hutool 获取元素类型并创建数组
            Class<T> elementType = (Class<T>) IterUtil.getElementType(from.iterator());
            T[] result = ArrayUtil.toArray(from, elementType);
            if (log.isDebugEnabled()) {
                log.debug("toArray: converted collection of size {} to array of type {}", from.size(), elementType.getName());
            }
            return result;
        } catch (Exception e) {
            log.warn("toArray: failed to get element type, fallback to Object[]", e);
            // 降级返回 Object[]
            return (T[]) from.toArray();
        }
    }

    /**
     * 安全地从数组中获取指定索引的元素。
     *
     * <p>若数组为 {@code null} 或索引越界，返回 {@code null} 并记录 debug 日志。
     *
     * @param array 数组（可为 {@code null}）
     * @param index 索引（从 0 开始）
     * @param <T>   元素类型
     * @return 对应索引的元素，若不存在则返回 {@code null}
     */
    public static <T> T get(T[] array, int index) {
        if (array == null) {
            if (log.isDebugEnabled()) {
                log.debug("get: array is null, returning null for index {}", index);
            }
            return null;
        }
        if (index < 0 || index >= array.length) {
            if (log.isDebugEnabled()) {
                log.debug("get: index {} out of bounds (length {}), returning null", index, array.length);
            }
            return null;
        }
        return array[index];
    }
}
