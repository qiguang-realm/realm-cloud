package cn.realm.cloud.framework.common.pojo;

import cn.realm.cloud.framework.common.enums.OrderDirection;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 排序字段 DTO
 * <p>
 * 类名加了 ing 的原因是，避免和 ES SortField 重名。
 * <p>
 * <b>安全说明：</b>使用此类构建 SQL 排序时，务必对 {@link #field} 进行白名单校验，
 * 防止 SQL 注入（例如仅允许表真实列名或预定义字段集合）。
 *
 * @author QI Guang
 */
@Data
@NoArgsConstructor
@Schema(description = "排序字段")
public class SortingField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 排序字段名（数据库列名或 DTO 字段名）
     * <p>业务层必须使用白名单校验，避免 SQL 注入。
     */
    @Schema(description = "排序字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "createTime")
    @NotBlank(message = "排序字段名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "字段名只能包含字母、数字和下划线")
    private String field;

    /**
     * 排序方向（使用枚举，类型安全）
     */
    @Schema(description = "排序方向", requiredMode = Schema.RequiredMode.REQUIRED, example = "desc")
    @NotNull(message = "排序方向不能为空")
    private OrderDirection order;

    /**
     * 全参构造器，用于方便创建实例
     *
     * @param field 字段名
     * @param order 排序方向
     */
    public SortingField(String field, OrderDirection order) {
        this.field = field;
        this.order = order;
    }

    /**
     * 兼容旧代码的构造器，接受字符串排序方向（不区分大小写）
     *
     * @param field 字段名
     * @param order 排序方向字符串，如 "asc" 或 "desc"
     */
    public SortingField(String field, String order) {
        this.field = field;
        this.order = OrderDirection.fromCode(order);
        if (this.order == null) {
            throw new IllegalArgumentException("无效的排序方向: " + order);
        }
    }

    /**
     * 是否为升序
     */
    public boolean isAsc() {
        return order == OrderDirection.ASC;
    }

    /**
     * 是否为降序
     */
    public boolean isDesc() {
        return order == OrderDirection.DESC;
    }

    /**
     * 转换为 SQL ORDER BY 子句片段
     * <p>示例：field ASC 或 field DESC
     */
    public String toSqlFragment() {
        return field + " " + order.getCode().toUpperCase();
    }

    /**
     * 获取排序方向字符串（兼容旧代码）
     *
     * @return "asc" 或 "desc"
     */
    public String getOrderString() {
        return order.getCode().toLowerCase();
    }

    /**
     * 静态工厂方法：升序
     */
    public static SortingField asc(String field) {
        return new SortingField(field, OrderDirection.ASC);
    }

    /**
     * 静态工厂方法：降序
     */
    public static SortingField desc(String field) {
        return new SortingField(field, OrderDirection.DESC);
    }

    /**
     * 自定义序列化：将 order 枚举序列化为小写字符串（与旧接口兼容）
     * 使用 @JsonProperty 确保 JSON 中字段名为 "order"，值为小写字符串。
     */
    @JsonProperty("order")
    public String getOrderValue() {
        return order.getCode().toLowerCase();
    }

    /**
     * 自定义反序列化：从字符串解析 order 枚举
     */
    @JsonCreator
    public static SortingField of(@JsonProperty("field") String field,
                                  @JsonProperty("order") String order) {
        return new SortingField(field, order);
    }
}
