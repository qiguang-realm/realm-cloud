package cn.realm.cloud.framework.common.util.collection;

import cn.hutool.core.collection.CollUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Set 工具类，提供集合创建与安全操作。
 *
 * <p>本工具类封装了常见的 Set 创建方法，增强空值安全性和日志记录，便于排查问题。
 *
 * @author QI Guang
 */
public class SetUtils {

    private static final Logger log = LoggerFactory.getLogger(SetUtils.class);

    /**
     * 私有构造方法，防止实例化。
     */
    private SetUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 将可变参数转换为 Set（去重，无序）。
     *
     * <p><b>特性说明：</b>
     * <ul>
     *   <li>若输入为 {@code null}，返回空 Set（不可变），并记录 DEBUG 日志。</li>
     *   <li>若输入为空数组，返回空 Set（不可变）。</li>
     *   <li>内部使用 {@link HashSet} 实现，保证元素唯一性。</li>
     *   <li>可变参数类型为泛型，支持任意类型元素。</li>
     * </ul>
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * Set<String> set = SetUtils.asSet("a", "b", "c");
     * Set<Integer> ints = SetUtils.asSet(1, 2, 3);
     * Set<Object> empty = SetUtils.asSet(); // 空 Set
     * Set<Object> safe = SetUtils.asSet(null); // 空 Set（日志记录）
     * }</pre>
     *
     * @param objs 可变参数，允许为 null
     * @param <T>  元素类型
     * @return 包含所有元素的 Set，若输入为 null 或空则返回空 Set（不可变）
     */
    @SafeVarargs
    public static <T> Set<T> asSet(T... objs) {
        if (objs == null) {
            if (log.isDebugEnabled()) {
                log.debug("SetUtils.asSet called with null argument, returning empty immutable set.");
            }
            return Collections.emptySet();
        }
        if (objs.length == 0) {
            if (log.isTraceEnabled()) {
                log.trace("SetUtils.asSet called with empty array, returning empty immutable set.");
            }
            return Collections.emptySet();
        }
        // 使用 Hutool 创建 HashSet（内部已处理重复元素）
        return CollUtil.newHashSet(objs);
    }

    /**
     * 从已有的集合创建 Set（复制元素）。
     *
     * <p><b>注意：</b>返回的 Set 是可变的，修改不会影响原集合。
     *
     * @param collection 原始集合，允许为 null
     * @param <T>        元素类型
     * @return 包含原集合元素的 Set，若输入为 null 则返回空 Set
     */
    public static <T> Set<T> asSet(Collection<? extends T> collection) {
        if (collection == null) {
            if (log.isDebugEnabled()) {
                log.debug("SetUtils.asSet called with null collection, returning empty immutable set.");
            }
            return Collections.emptySet();
        }
        if (collection.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("SetUtils.asSet called with empty collection, returning empty immutable set.");
            }
            return Collections.emptySet();
        }
        return new HashSet<>(collection);
    }
}
