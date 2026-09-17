package cn.realm.cloud.framework.common.util.object;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Object 工具类，提供对象克隆、比较、空值处理等常用操作。
 *
 * @author QI Guang
 */
public class ObjectUtils {

    private static final Logger log = LoggerFactory.getLogger(ObjectUtils.class);

    private ObjectUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 克隆对象，并将克隆对象的 id 字段设置为 null，然后执行后置处理。
     *
     * <p>该方法会先尝试克隆源对象（使用 {@link ObjectUtil#clone}），
     * 如果克隆成功且源对象中存在名为 "id" 的字段，则将其值置为 null，
     * 最后执行自定义的 Consumer 对克隆对象进行二次编辑。
     *
     * <p><b>注意：</b>源对象必须实现 {@link Cloneable} 接口，否则克隆可能失败。
     *
     * @param object   源对象（可为 null）
     * @param consumer 后置处理函数（可为 null），在克隆完成后执行
     * @param <T>      对象类型
     * @return 克隆后的对象，若源对象为 null 或克隆失败则返回 null
     */
    public static <T> T cloneIgnoreId(T object, Consumer<T> consumer) {
        if (object == null) {
            log.trace("cloneIgnoreId: source object is null, returning null");
            return null;
        }

        T result;
        try {
            result = ObjectUtil.clone(object);
            if (result == null) {
                log.warn("cloneIgnoreId: failed to clone object of type {} (maybe not Cloneable)", object.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            log.error("cloneIgnoreId: clone failed for object of type {}", object.getClass().getName(), e);
            return null;
        }

        // 清空 id 字段（如果存在）
        try {
            Field field = ReflectUtil.getField(object.getClass(), "id");
            if (field != null) {
                ReflectUtil.setFieldValue(result, field, null);
                if (log.isTraceEnabled()) {
                    log.trace("cloneIgnoreId: set id field to null for object of type {}", object.getClass().getName());
                }
            } else {
                log.trace("cloneIgnoreId: no 'id' field found in class {}", object.getClass().getName());
            }
        } catch (Exception e) {
            log.warn("cloneIgnoreId: failed to set id field to null for type {}", object.getClass().getName(), e);
        }

        // 执行后置处理
        if (consumer != null) {
            try {
                consumer.accept(result);
                if (log.isTraceEnabled()) {
                    log.trace("cloneIgnoreId: consumer executed for object of type {}", object.getClass().getName());
                }
            } catch (Exception e) {
                log.error("cloneIgnoreId: consumer execution failed for object of type {}", object.getClass().getName(), e);
            }
        }

        return result;
    }

    /**
     * 返回两个可比较对象中的较大者。
     *
     * <p>如果两个对象都为 null，返回 null；
     * 如果其中一个为 null，返回非 null 的那个。
     *
     * @param obj1 第一个对象（可为 null）
     * @param obj2 第二个对象（可为 null）
     * @param <T>  实现了 Comparable 接口的类型
     * @return 较大的对象，若两者都为 null 则返回 null
     */
    public static <T extends Comparable<T>> T max(T obj1, T obj2) {
        if (obj1 == null) {
            log.trace("max: obj1 is null, returning obj2");
            return obj2;
        }
        if (obj2 == null) {
            log.trace("max: obj2 is null, returning obj1");
            return obj1;
        }
        T result = obj1.compareTo(obj2) > 0 ? obj1 : obj2;
        log.trace("max: result = {}", result);
        return result;
    }

    /**
     * 返回可变参数中第一个非 null 的元素。
     *
     * <p>如果所有参数都为 null，则返回 null。
     *
     * @param array 可变参数数组（可为 null）
     * @param <T>   元素类型
     * @return 第一个非 null 的元素，若数组为 null 或所有元素都为 null 则返回 null
     */
    @SafeVarargs
    public static <T> T defaultIfNull(T... array) {
        if (array == null || array.length == 0) {
            log.trace("defaultIfNull: array is null or empty, returning null");
            return null;
        }
        for (T item : array) {
            if (item != null) {
                log.trace("defaultIfNull: found first non-null element: {}", item);
                return item;
            }
        }
        log.trace("defaultIfNull: all elements are null, returning null");
        return null;
    }

    /**
     * 判断对象是否等于数组中的任意一个元素（使用 equals 方法）。
     *
     * <p>如果对象为 null，则判断数组中是否有 null 元素。
     *
     * @param obj   待比较的对象（可为 null）
     * @param array 候选数组（可为 null）
     * @param <T>   对象类型
     * @return 如果 obj 在数组中存在（包含 null 匹配），返回 true；否则 false
     */
    @SafeVarargs
    public static <T> boolean equalsAny(T obj, T... array) {
        if (array == null || array.length == 0) {
            log.trace("equalsAny: array is null or empty, returning false");
            return false;
        }
        boolean result = Arrays.asList(array).contains(obj);
        log.trace("equalsAny: obj={}, array={}, result={}", obj, Arrays.toString(array), result);
        return result;
    }

    /**
     * 判断多个对象是否不全为空（即至少有一个非空）。
     *
     * <p>该方法为 {@link ObjectUtil#isAllEmpty} 的取反。
     *
     * @param objs 待检查的对象数组
     * @return 如果至少有一个对象非空，返回 true；否则返回 false
     */
    public static boolean isNotAllEmpty(Object... objs) {
        boolean result = !ObjectUtil.isAllEmpty(objs);
        if (log.isTraceEnabled()) {
            log.trace("isNotAllEmpty: objs={}, result={}", Arrays.toString(objs), result);
        }
        return result;
    }
}
