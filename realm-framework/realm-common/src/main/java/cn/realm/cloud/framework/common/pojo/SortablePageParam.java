package cn.realm.cloud.framework.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 可排序的分页参数（框架无关）。
 * <p>在 {@link PageParam} 基础上增加排序表达式，遵循 JSON:API 的 sort 参数规范：
 * <ul>
 *     <li>多字段以英文逗号分隔，如 {@code sort=name,createdAt}</li>
 *     <li>降序在字段名前加 {@code -} 前缀，如 {@code sort=-createdAt,name}</li>
 *     <li>默认升序</li>
 * </ul>
 * <p>本类只负责解析排序表达式，输出框架无关的 {@link SortField} 列表；
 * 转换为 MyBatis-Plus 的 {@code OrderItem} 或 Spring Data 的 {@code Sort.Order}
 * 由各自的 starter 完成。
 *
 * @author QI Guang
 */
@Schema(description = "可排序的分页参数")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SortablePageParam extends PageParam {

    private static final long serialVersionUID = 1L;

    /**
     * 排序表达式，格式为逗号分隔的字段列表，降序字段加 {@code -} 前缀。
     * <p>示例：{@code -createdAt,name} 表示先按 createdAt 降序，再按 name 升序。
     */
    @Schema(description = "排序表达式，降序字段加 '-' 前缀，多字段以逗号分隔。示例：-createdAt,name",
            example = "-createdAt,name")
    @Pattern(regexp = "^-?[a-zA-Z0-9_]+(,-?[a-zA-Z0-9_]+)*$",
            message = "排序表达式格式错误，仅允许字母、数字、下划线，降序字段以 '-' 开头，多字段以逗号分隔")
    private String sort;

    /**
     * 判断是否存在排序表达式。
     */
    public boolean hasSort() {
        return sort != null && !sort.isBlank();
    }

    // ---------- 工厂方法 ----------

    /**
     * 快速构建一个可排序的分页请求。
     */
    public static SortablePageParam of(int pageNum, int pageSize, String sort) {
        SortablePageParam param = new SortablePageParam();
        param.setPageNum(pageNum);
        param.setPageSize(pageSize);
        param.setSort(sort);
        return param;
    }

    // ---------- 排序解析 ----------

    /**
     * 解析排序表达式为框架无关的 {@link SortField} 列表。
     * <p><b>安全提示：</b>仅保留 {@code allowedFields} 白名单内的字段，
     * 防止 SQL 注入。调用方必须传入明确的允许排序字段集合，绝不能为空或包含用户输入。
     *
     * @param allowedFields 允许排序的字段名集合（通常来自实体类的可排序列）
     * @return 解析后的排序字段列表；若无合法字段，返回空列表
     */
    public List<SortField> parseSortFields(Set<String> allowedFields) {
        if (!hasSort() || allowedFields == null || allowedFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<SortField> fields = new ArrayList<>();
        for (String part : sort.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean asc = true;
            String field = trimmed;
            if (field.startsWith("-")) {
                asc = false;
                field = field.substring(1);
            }
            if (allowedFields.contains(field)) {
                fields.add(asc ? SortField.asc(field) : SortField.desc(field));
            }
        }
        return fields;
    }
}
