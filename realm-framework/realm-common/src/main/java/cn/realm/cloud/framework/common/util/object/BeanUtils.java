package cn.realm.cloud.framework.common.util.object;

import cn.hutool.core.bean.BeanUtil;
import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.framework.common.util.collection.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Bean 工具类，基于 Hutool 的 BeanUtil 实现对象转换与属性拷贝。
 *
 * <p>默认使用 {@link BeanUtil} 作为底层实现，性能足够满足绝大多数业务场景。
 * 对于复杂的对象转换，建议使用 MapStruct 等工具。
 *
 * @author QI Guang
 */
public class BeanUtils {

    private static final Logger log = LoggerFactory.getLogger(BeanUtils.class);

    private BeanUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 将源对象转换为目标类型的实例。
     *
     * @param source      源对象（可为 null）
     * @param targetClass 目标类型
     * @param <T>         目标类型
     * @return 转换后的对象，若 source 为 null 则返回 null
     * @throws RuntimeException 转换失败时抛出，包含原始异常
     */
    public static <T> T toBean(Object source, Class<T> targetClass) {
        if (source == null) {
            log.trace("toBean: source is null, returning null");
            return null;
        }
        if (targetClass == null) {
            throw new IllegalArgumentException("targetClass must not be null");
        }
        try {
            T result = BeanUtil.toBean(source, targetClass);
            if (log.isTraceEnabled()) {
                log.trace("toBean: converted {} to {}",
                        source.getClass().getSimpleName(), targetClass.getSimpleName());
            }
            return result;
        } catch (Exception e) {
            log.error("toBean: failed to convert {} to {}",
                    source.getClass().getSimpleName(), targetClass.getSimpleName(), e);
            throw new RuntimeException("Bean conversion failed", e);
        }
    }

    /**
     * 将源对象转换为目标类型，并执行后置处理。
     *
     * @param source      源对象（可为 null）
     * @param targetClass 目标类型
     * @param peek        后置处理函数（不为 null 时执行）
     * @param <T>         目标类型
     * @return 转换后的对象，若 source 为 null 则返回 null
     */
    public static <T> T toBean(Object source, Class<T> targetClass, Consumer<T> peek) {
        T target = toBean(source, targetClass);
        if (target != null && peek != null) {
            peek.accept(target);
        }
        return target;
    }

    /**
     * 将源对象列表转换为目标类型列表。
     *
     * @param source     源列表（可为 null）
     * @param targetType 目标类型
     * @param <S>        源类型
     * @param <T>        目标类型
     * @return 转换后的列表，若源列表为 null 则返回 null
     */
    public static <S, T> List<T> toBean(List<S> source, Class<T> targetType) {
        if (source == null) {
            log.trace("toBean (list): source list is null, returning null");
            return null;
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        List<T> result = CollectionUtils.convertList(source, s -> toBean(s, targetType));
        if (log.isDebugEnabled()) {
            log.debug("toBean (list): converted {} items from {} to {}",
                    source.size(), source.getClass().getSimpleName(), targetType.getSimpleName());
        }
        return result;
    }

    /**
     * 将源对象列表转换为目标类型列表，并对每个转换后的对象执行后置处理。
     *
     * @param source     源列表（可为 null）
     * @param targetType 目标类型
     * @param peek       后置处理函数（不为 null 时对每个元素执行）
     * @param <S>        源类型
     * @param <T>        目标类型
     * @return 转换后的列表，若源列表为 null 则返回 null
     */
    public static <S, T> List<T> toBean(List<S> source, Class<T> targetType, Consumer<T> peek) {
        List<T> list = toBean(source, targetType);
        if (list != null && peek != null) {
            list.forEach(peek);
        }
        return list;
    }

    /**
     * 将分页结果中的列表转换为目标类型，生成新的分页结果。
     *
     * @param source     源分页结果（可为 null）
     * @param targetType 目标类型
     * @param <S>        源类型
     * @param <T>        目标类型
     * @return 转换后的分页结果，若源分页结果为 null 则返回 null
     */
    public static <S, T> PageResult<T> toBean(PageResult<S> source, Class<T> targetType) {
        return toBean(source, targetType, null);
    }

    /**
     * 将分页结果中的列表转换为目标类型，并对每个转换后的对象执行后置处理。
     *
     * @param source     源分页结果（可为 null）
     * @param targetType 目标类型
     * @param peek       后置处理函数（不为 null 时对每个元素执行）
     * @param <S>        源类型
     * @param <T>        目标类型
     * @return 转换后的分页结果，若源分页结果为 null 则返回 null
     */
    public static <S, T> PageResult<T> toBean(PageResult<S> source, Class<T> targetType, Consumer<T> peek) {
        if (source == null) {
            log.trace("toBean (page): source page result is null, returning null");
            return null;
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        List<T> list = toBean(source.getList(), targetType);
        if (peek != null && list != null) {
            list.forEach(peek);
        }
        PageResult<T> result = new PageResult<>(list, source.getTotal());
        if (log.isDebugEnabled()) {
            log.debug("toBean (page): converted {} items, total={}",
                    list == null ? 0 : list.size(), source.getTotal());
        }
        return result;
    }

    /**
     * 将源对象的属性复制到目标对象。
     * <p>忽略空值（即源对象为 null 的属性不会覆盖目标对象的现有值）。
     *
     * @param source 源对象（可为 null）
     * @param target 目标对象（不可为 null）
     * @throws IllegalArgumentException 如果 target 为 null
     * @throws RuntimeException         如果复制过程中发生错误
     */
    public static void copyProperties(Object source, Object target) {
        if (source == null) {
            log.trace("copyProperties: source is null, skip copying");
            return;
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        try {
            // false 表示忽略空值
            BeanUtil.copyProperties(source, target, false);
            if (log.isTraceEnabled()) {
                log.trace("copyProperties: copied from {} to {}",
                        source.getClass().getSimpleName(), target.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("copyProperties: failed to copy from {} to {}",
                    source.getClass().getSimpleName(), target.getClass().getSimpleName(), e);
            throw new RuntimeException("Bean copy failed", e);
        }
    }
}
