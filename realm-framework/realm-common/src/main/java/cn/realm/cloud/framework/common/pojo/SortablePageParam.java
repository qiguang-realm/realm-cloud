package cn.realm.cloud.framework.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 可排序的分页参数
 *
 * <p>在 {@link PageParam} 基础上增加排序字段列表，支持多字段排序。
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
     * 排序字段列表（按顺序优先级排序）
     */
    @Schema(description = "排序字段列表，按顺序优先级排序")
    @Valid
    private List<SortingField> sortingFields;

    /**
     * 是否有排序字段
     */
    public boolean hasSortingFields() {
        return sortingFields != null && !sortingFields.isEmpty();
    }

    /**
     * 获取非空的排序字段列表（避免 NPE）
     */
    public List<SortingField> getSafeSortingFields() {
        return sortingFields == null ? Collections.emptyList() : sortingFields;
    }

    /**
     * 将排序字段列表转换为 SQL ORDER BY 子句片段
     * <p>仅返回白名单内的字段，防止 SQL 注入。
     *
     * @param allowedFields 允许排序的字段名集合（通常来自实体类的可排序列）
     * @return ORDER BY 子句内容，若无合法排序字段则返回空字符串
     */
    public String toOrderByClause(Set<String> allowedFields) {
        if (!hasSortingFields() || allowedFields == null || allowedFields.isEmpty()) {
            return "";
        }
        return sortingFields.stream()
                .filter(f -> allowedFields.contains(f.getField()))
                .map(SortingField::toSqlFragment)
                .collect(Collectors.joining(", "));
    }
}
