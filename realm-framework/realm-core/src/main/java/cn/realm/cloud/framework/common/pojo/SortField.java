package cn.realm.cloud.framework.common.pojo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 框架无关的排序字段描述。
 * <p>用于承载 {@link SortablePageParam} 解析后的排序结果，
 * 由上层 starter 转换为 MyBatis-Plus 的 {@code OrderItem} 或
 * Spring Data 的 {@code Sort.Order}。
 *
 * @author QI Guang
 */
public final class SortField implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字段名。
     */
    private final String field;

    /**
     * 排序方向。
     */
    private final Direction direction;

    // ---------- 构造器 ----------

    public SortField(String field, Direction direction) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.direction = Objects.requireNonNull(direction, "direction must not be null");
    }

    // ---------- 静态工厂方法 ----------

    /**
     * 构建升序排序字段。
     */
    public static SortField asc(String field) {
        return new SortField(field, Direction.ASC);
    }

    /**
     * 构建降序排序字段。
     */
    public static SortField desc(String field) {
        return new SortField(field, Direction.DESC);
    }

    // ---------- 访问器 ----------

    public String getField() {
        return field;
    }

    public Direction getDirection() {
        return direction;
    }

    // ---------- 便捷方法 ----------

    /**
     * 是否升序。
     */
    public boolean isAsc() {
        return direction == Direction.ASC;
    }

    /**
     * 是否降序。
     */
    public boolean isDesc() {
        return direction == Direction.DESC;
    }

    // ---------- equals / hashCode / toString ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SortField other)) {
            return false;
        }
        return field.equals(other.field) && direction == other.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, direction);
    }

    @Override
    public String toString() {
        return "SortField{field='" + field + "', direction=" + direction + '}';
    }

    // ---------- 排序方向枚举 ----------

    /**
     * 排序方向。
     */
    public enum Direction {
        /**
         * 升序
         */
        ASC,
        /**
         * 降序
         */
        DESC
    }
}
